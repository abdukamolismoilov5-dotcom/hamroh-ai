package uz.hamroh.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import uz.hamroh.core.vision.Detection
import uz.hamroh.core.vision.LumaOps
import java.io.File
import java.util.concurrent.Executors

/**
 * Kamera boshqaruvi: yo'l tahlili (FR-4), haydovchi yuzi (FR-5) va videoregistrator (FR-6) bitta joyda.
 * Barcha bind/unbind asosiy oqimda. Tahlil alohida oqimda, natijalar [Listener] orqali.
 */
class CameraHub(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val dashcam: Dashcam,
    private val listener: Listener,
) {
    interface Listener {
        fun onRoadDetections(timeMs: Long, detections: List<Detection>, meanLuma: Int, fps: Float)
        fun onEar(timeMs: Long, ear: Float?)
        fun onCameraStatus(status: String)
    }

    private val main = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var road: RoadDetector? = null
    private var face: FaceMonitor? = null

    /** "road" | "driver" | "off" */
    @Volatile var mode: String = "off"
        private set
    @Volatile private var active = false
    @Volatile var analysisEnabled = true
    /** Inferenslar orasidagi minimal vaqt (issiqlik rejimiga qarab o'zgaradi), ms. */
    @Volatile var intervalMs = 65L
    @Volatile var dashcamWanted = true
    private var previewProvider: Preview.SurfaceProvider? = null
    private var previewUseCase: Preview? = null
    private var lastInfer = 0L
    private var fpsWindowStart = 0L
    private var fpsCount = 0
    private var fps = 0f
    @Volatile private var nightHint = false

    val isPreviewAttached: Boolean
        get() = previewProvider != null

    init {
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({
            provider = runCatching { f.get() }.getOrNull()
            rebind()
        }, main)
    }

    fun configure(mode: String, active: Boolean) {
        main.execute {
            if (this.mode == mode && this.active == active) return@execute
            this.mode = mode; this.active = active
            rebind()
        }
    }

    fun setPreview(surface: Preview.SurfaceProvider?) {
        main.execute {
            previewProvider = surface
            val existing = previewUseCase
            if (existing != null && active && mode != "off") {
                runCatching { existing.setSurfaceProvider(surface) }
            } else if (surface != null && active && mode != "off") {
                rebind()
            }
        }
    }

    private fun ensureModels() {
        if (mode == "road" && road == null) road = RoadDetector(context)
        if (mode == "driver" && face == null) face = FaceMonitor(context)
    }

    private fun rebind() {
        val p = provider ?: return
        dashcam.stopRecording()
        previewUseCase = null
        runCatching { p.unbindAll() }
        if (!active || mode == "off") { listener.onCameraStatus("off"); return }
        analysisExecutor.execute { ensureModels() }
        val preferred = if (mode == "driver") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val fallbackSelector = if (mode == "driver") CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val selector = when {
            runCatching { p.hasCamera(preferred) }.getOrDefault(false) -> preferred
            runCatching { p.hasCamera(fallbackSelector) }.getOrDefault(false) -> fallbackSelector
            else -> preferred
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { analyze(it) }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewProvider) }
        previewUseCase = preview
        val useCases = mutableListOf<UseCase>(preview, analysis)
        var video: VideoCapture<Recorder>? = null
        if (mode == "road" && dashcamWanted && dashcam.hasSpace()) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            video = VideoCapture.withOutput(recorder)
            useCases += video
        }
        try {
            p.bindToLifecycle(owner, selector, *useCases.toTypedArray())
        } catch (e: Throwable) {
            Log.w("CameraHub", "bind xatosi, videosiz urinamiz", e)
            video = null
            runCatching { p.unbindAll() }
            val fallback = useCases.filter { it !is VideoCapture<*> }
            try {
                p.bindToLifecycle(owner, selector, *fallback.toTypedArray())
            } catch (e2: Throwable) {
                Log.e("CameraHub", "kamera ochilmadi", e2)
                previewUseCase = null
                listener.onCameraStatus("error")
                return
            }
        }
        listener.onCameraStatus(if (mode == "driver") "driver" else "road")
        video?.let { dashcam.attach(it) }
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!analysisEnabled) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastInfer < intervalMs) return
            lastInfer = now
            ensureModels()
            val w = image.width; val h = image.height
            if (w <= 0 || h <= 0 || image.planes.isEmpty()) return
            val yPlane = image.planes[0]
            val stride = maxOf(w, yPlane.rowStride)
            val buf = yPlane.buffer
            val limit = buf.limit()
            // Tezkor namuna olish orqali o'rtacha yorug'likni hisoblash (<0.1ms)
            var sumLuma = 0L
            var countLuma = 0
            val stepY = maxOf(1, h / 24)
            val stepX = maxOf(1, w / 32)
            for (ry in 0 until h step stepY) {
                val rowOff = ry * stride
                for (rx in 0 until w step stepX) {
                    val idx = rowOff + rx
                    if (idx < limit) {
                        sumLuma += (buf.get(idx).toInt() and 0xFF)
                        countLuma++
                    }
                }
            }
            val luma = if (countLuma > 0) (sumLuma / countLuma).toInt() else 120
            nightHint = LumaOps.isNight(luma)
            val src: Bitmap = if (nightHint && mode == "road") {
                buf.rewind()
                val y = ByteArray(stride * h)
                val toRead = minOf(buf.remaining(), y.size)
                if (toRead > 0) buf.get(y, 0, toRead)
                buf.rewind()
                // FR-4.11: qarshi faralarni niqoblash va CLAHE, keyin kulrang tasvir
                LumaOps.maskGlare(y, w, h, stride)
                LumaOps.clahe(y, w, h, stride)
                grayBitmap(y, w, h, stride)
            } else {
                try {
                    image.toBitmap()
                } catch (_: Throwable) {
                    buf.rewind()
                    val y = ByteArray(stride * h)
                    val toRead = minOf(buf.remaining(), y.size)
                    if (toRead > 0) buf.get(y, 0, toRead)
                    grayBitmap(y, w, h, stride)
                }
            }
            val rot = image.imageInfo.rotationDegrees
            val bmp = if (rot != 0) Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(rot.toFloat()) }, true) else src
            when (mode) {
                "road" -> {
                    val det = road ?: return
                    val dets = det.detect(bmp, now)
                    tickFps(now)
                    listener.onRoadDetections(now, dets, luma, fps)
                }
                "driver" -> {
                    val f = face ?: return
                    tickFps(now)
                    listener.onEar(now, f.ear(bmp, now))
                }
            }
        } catch (e: Throwable) {
            Log.w("CameraHub", "tahlil xatosi", e)
        } finally {
            runCatching { image.close() }
        }
    }

    private fun tickFps(now: Long) {
        if (fpsWindowStart == 0L) fpsWindowStart = now
        fpsCount++
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1_000) {
            fps = fpsCount * 1000f / elapsed
            fpsCount = 0; fpsWindowStart = now
        } else if (fps == 0f && elapsed > 0) {
            fps = fpsCount * 1000f / elapsed
        }
    }

    private fun grayBitmap(y: ByteArray, w: Int, h: Int, stride: Int): Bitmap {
        val px = IntArray(w * h)
        for (r in 0 until h) {
            val b = r * stride
            for (c in 0 until w) {
                val idx = b + c
                val v = if (idx < y.size) (y[idx].toInt() and 0xFF) else 0
                px[r * w + c] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun release() {
        main.execute {
            dashcam.stopRecording()
            previewUseCase = null
            runCatching { provider?.unbindAll() }
            analysisExecutor.execute { road?.close(); face?.close(); road = null; face = null }
            analysisExecutor.shutdown()
        }
    }
}

/**
 * FR-6: aylanma videoyozuv. 60 s bo'laklar, oxirgi 5 tasi saqlanadi; zarba bo'lsa oldingi va keyingi bo'laklar
 * "protected" papkaga ko'chiriladi va yonida SHA-256 xeshli JSON (vaqt, tezlik, koordinata) yoziladi (FR-6.3).
 */
class Dashcam(private val context: Context, private val onClip: (File, String?, Boolean, String) -> Unit, private val onState: (Boolean) -> Unit) {
    private val dir = File(context.filesDir, "dashcam").apply { mkdirs() }
    private val protectedDir = File(dir, "protected").apply { mkdirs() }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    @Volatile private var sessionGen = 0L
    private var currentFile: File? = null
    private var currentStart = 0L
    private val meta = StringBuilder()
    @Volatile private var protectFrom = Long.MAX_VALUE
    @Volatile private var protectUntil = Long.MIN_VALUE
    @Volatile private var protectReason = "impact"
    private val recent = ArrayDeque<Triple<File, Long, Long>>() // fayl, boshlanish, tugash (epoch ms)
    private val io = Executors.newSingleThreadExecutor()

    fun hasSpace(): Boolean = dir.usableSpace > 1L * 1024 * 1024 * 1024

    fun attach(vc: VideoCapture<Recorder>) {
        sessionGen++
        capture = vc
        recording = null
        startSegment(sessionGen)
    }

    private val rotate = Runnable {
        val r = recording
        recording = null
        runCatching { r?.stop() }
    }

    private fun startSegment(gen: Long = sessionGen) {
        if (gen != sessionGen || recording != null) return
        val vc = capture ?: return
        if (!hasSpace()) { onState(false); return }
        val now = System.currentTimeMillis()
        val f = File(dir, "seg_$now.mp4")
        currentFile = f; currentStart = now
        synchronized(meta) { meta.setLength(0) }
        try {
            recording = vc.output.prepareRecording(context, FileOutputOptions.Builder(f).build())
                .start(ContextCompat.getMainExecutor(context)) { e ->
                    when (e) {
                        is VideoRecordEvent.Start -> if (gen == sessionGen) onState(true)
                        is VideoRecordEvent.Finalize -> {
                            val end = System.currentTimeMillis()
                            val metaText = synchronized(meta) { meta.toString() }
                            val start = currentStart
                            if (gen == sessionGen) recording = null
                            finalizeSegment(f, start, end, metaText, e.hasError())
                            if (gen == sessionGen && capture != null) startSegment(gen) else if (gen == sessionGen) onState(false)
                        }
                        else -> Unit
                    }
                }
            main.removeCallbacks(rotate)
            main.postDelayed(rotate, 60_000)
        } catch (e: Throwable) {
            Log.w("Dashcam", "videoyozuv boshlanmadi", e)
            recording = null
            onState(false)
        }
    }

    /** Har soniyada xizmat chaqiradi: klip yonidagi metama'lumot. */
    fun appendMeta(lat: Double?, lon: Double?, speedKmh: Double) {
        synchronized(meta) {
            if (meta.isNotEmpty()) meta.append(",\n")
            meta.append("{\"t\":${System.currentTimeMillis()},\"lat\":${lat ?: "null"},\"lon\":${lon ?: "null"},\"kmh\":${"%.1f".format(java.util.Locale.US, speedKmh)}}")
        }
    }

    /** FR-6.2: zarba yoki qo'lda — oldingi 30 va keyingi 30 soniya himoyalanadi. */
    fun protect(reason: String) {
        val now = System.currentTimeMillis()
        protectFrom = now - 30_000
        protectUntil = now + 30_000
        protectReason = reason
        // allaqachon yopilgan bo'laklar (recent faqat io oqimida o'zgaradi)
        io.execute {
            recent.filter { (_, s, e) -> e >= protectFrom && s <= protectUntil }.forEach { (f, s, _) -> copyProtected(f, s) }
        }
    }

    private fun finalizeSegment(f: File, start: Long, end: Long, metaText: String, error: Boolean) {
        io.execute {
            if (error && (!f.exists() || f.length() < 1024)) { f.delete(); return@execute }
            File(f.path.removeSuffix(".mp4") + ".json").writeText("[\n$metaText\n]")
            recent.addLast(Triple(f, start, end))
            if (end >= protectFrom && start <= protectUntil) copyProtected(f, start)
            onClip(f, null, false, "loop")
            // FR-6.1: oxirgi 5 bo'lak
            while (recent.size > 5) {
                val (old, _, _) = recent.removeFirst()
                old.delete(); File(old.path.removeSuffix(".mp4") + ".json").delete()
            }
        }
    }

    private fun copyProtected(f: File, start: Long) {
        val target = File(protectedDir, f.name)
        if (target.exists() || !f.exists()) return
        f.copyTo(target)
        val sha = sha256(target)
        val json = File(f.path.removeSuffix(".mp4") + ".json")
        val metaText = if (json.exists()) json.readText() else "[]"
        File(protectedDir, f.name.removeSuffix(".mp4") + ".json")
            .writeText("{\"video\":\"${target.name}\",\"sha256\":\"$sha\",\"started_at\":$start,\"reason\":\"$protectReason\",\"track\":$metaText}")
        onClip(target, sha, true, protectReason)
    }

    fun stopRecording() {
        sessionGen++
        main.removeCallbacks(rotate)
        val r = recording
        capture = null
        recording = null
        runCatching { r?.stop() }
    }

    companion object {
        fun sha256(f: File): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

