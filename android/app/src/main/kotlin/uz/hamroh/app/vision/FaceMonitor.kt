package uz.hamroh.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import uz.hamroh.core.driver.Ear
import uz.hamroh.core.driver.P2

/**
 * FR-5.1/5.2: old kamera orqali ko'z holati. Faqat EAR soni hisoblanadi; kadr saqlanmaydi va hech qayerga yuborilmaydi.
 * MediaPipe Face Landmarker (468 nuqta). Ko'z nuqtalari: chap 33,160,158,133,153,144; o'ng 362,385,387,263,373,380.
 */
class FaceMonitor(context: Context) : AutoCloseable {
    private var lastTs = 0L
    private val landmarker: FaceLandmarker? = try {
        val base = BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build()
        FaceLandmarker.createFromOptions(
            context,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                .build(),
        )
    } catch (e: Throwable) {
        Log.w("FaceMonitor", "model yuklanmadi", e); null
    }

    val available get() = landmarker != null

    /** Ikki ko'zning o'rtacha EAR qiymati yoki null (yuz topilmadi). */
    fun ear(bitmap: Bitmap, timestampMs: Long): Float? {
        val lm = landmarker ?: return null
        val ts = maxOf(timestampMs, lastTs + 1L)
        lastTs = ts
        val res = try {
            lm.detectForVideo(BitmapImageBuilder(bitmap).build(), ts)
        } catch (e: Throwable) {
            Log.w("FaceMonitor", "detectForVideo xatosi", e)
            return null
        }
        val face = res.faceLandmarks().firstOrNull() ?: return null
        if (face.size < 388) return null
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        fun p(i: Int): P2 { val l: NormalizedLandmark = face[i]; return P2(l.x() * w, l.y() * h) }
        val left = Ear.of(p(33), p(160), p(158), p(133), p(153), p(144))
        val right = Ear.of(p(362), p(385), p(387), p(263), p(373), p(380))
        return (left + right) / 2f
    }

    override fun close() { runCatching { landmarker?.close() } }

    companion object { const val MODEL = "face_landmarker.task" }
}

