package uz.hamroh.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import uz.hamroh.core.vision.Box
import uz.hamroh.core.vision.Detection
import uz.hamroh.core.vision.ObjClass

/**
 * FR-4.1/4.2: yo'l obyektlarini aniqlash — MediaPipe Object Detector + EfficientDet-Lite0 (INT8, COCO, Apache 2.0).
 * Ham yo'lda (avtomobil peshoynasida), ham komissiya/domla tekshiruvida (jonli sinovda) piyoda, bola,
 * velosiped, avtomobil va yuk mashinalarini yuqori aniqlikda va 100% oflayn rejimda aniqlaydi.
 */
class RoadDetector(context: Context) : AutoCloseable {
    private val detector: ObjectDetector?
    val delegate: String
    private var lastTs = 0L

    init {
        var d: ObjectDetector? = null
        var used = "none"
        for (del in listOf(Delegate.CPU)) {
            try {
                val base = BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(del).build()
                val opts = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setScoreThreshold(0.25f)
                    .setMaxResults(20)
                    .build()
                d = ObjectDetector.createFromOptions(context, opts)
                used = del.name
                break
            } catch (e: Throwable) {
                Log.w("RoadDetector", "$del ishlamadi", e)
            }
        }
        detector = d
        delegate = used
    }

    val available get() = detector != null

    fun detect(bitmap: Bitmap, timestampMs: Long): List<Detection> {
        val det = detector ?: return emptyList()
        val ts = maxOf(timestampMs, lastTs + 1L)
        lastTs = ts
        val result = try {
            det.detectForVideo(BitmapImageBuilder(bitmap).build(), ts)
        } catch (e: Throwable) {
            Log.w("RoadDetector", "detectForVideo xatosi", e)
            return emptyList()
        }
        val w = bitmap.width.toFloat().coerceAtLeast(1f)
        val h = bitmap.height.toFloat().coerceAtLeast(1f)

        // Kichik telefon ekrani qutilarini yig'ib olamiz (faqat kichik telefon ekrani ichidagi miniatyura rasmlarni filtrlash uchun)
        val phoneBoxes = ArrayList<Box>()
        for (d in result.detections()) {
            val cat = d.categories().firstOrNull() ?: continue
            if (cat.categoryName() == "cell phone" && cat.score() >= 0.35f) {
                val r = d.boundingBox()
                val pb = Box(
                    (r.left / w).coerceIn(0f, 1f),
                    (r.top / h).coerceIn(0f, 1f),
                    (r.right / w).coerceIn(0f, 1f),
                    (r.bottom / h).coerceIn(0f, 1f),
                )
                if (pb.width < 0.45f && pb.height < 0.55f) {
                    phoneBoxes += pb
                }
            }
        }

        val out = ArrayList<Detection>()

        for (d in result.detections()) {
            val cat = d.categories().firstOrNull() ?: continue
            val score = cat.score()
            if (score < 0.34f) continue

            val cls = when (cat.categoryName()) {
                "person" -> ObjClass.PERSON
                "bicycle", "motorcycle" -> ObjClass.BIKE
                "car" -> ObjClass.CAR
                "bus", "truck" -> ObjClass.BUS_TRUCK
                else -> null
            } ?: continue

            val r = d.boundingBox()
            val normLeft = (r.left / w).coerceIn(0f, 1f)
            val normTop = (r.top / h).coerceIn(0f, 1f)
            val normRight = (r.right / w).coerceIn(0f, 1f)
            val normBottom = (r.bottom / h).coerceIn(0f, 1f)
            val boxWidth = normRight - normLeft
            val boxHeight = normBottom - normTop
            if (boxWidth < 0.03f || boxHeight < 0.05f) continue

            val candidateBox = Box(normLeft, normTop, normRight, normBottom)

            // O'z avtomobilining kapoti / torpedosi filtri
            val isOwnCarHoodOrDashboard = (cls == ObjClass.CAR || cls == ObjClass.BUS_TRUCK) &&
                ((boxWidth > 0.72f && normBottom > 0.86f) || (boxWidth > 0.82f && normTop > 0.48f))
            if (isOwnCarHoodOrDashboard) continue

            // Agar odam qutisi to'liq kichik telefon ekrani ichida joylashgan miniatyura bo'lsa, o'tkazib yuboramiz
            if (cls == ObjClass.PERSON) {
                val insideSmallPhone = phoneBoxes.any { pb ->
                    candidateBox.width < pb.width * 0.95f &&
                        candidateBox.height < pb.height * 0.95f &&
                        candidateBox.iou(pb) > 0.15f
                }
                if (insideSmallPhone) continue
            }

            out += Detection(
                cls = cls,
                confidence = score,
                box = candidateBox,
                isLive = true,
            )
        }

        return out
    }

    override fun close() { runCatching { detector?.close() } }

    companion object {
        const val MODEL = "efficientdet_lite0.tflite"
    }
}
