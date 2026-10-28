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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * FR-4.1/4.2: yo'l obyektlarini aniqlash — MediaPipe Object Detector + EfficientDet-Lite0 (INT8, COCO, Apache 2.0).
 * Maxsus 4-bosqichli Anti-Spoofing / Jonlilik filtri (2D rasm, telefon ekrani, plakat va o'z mashinasi kapotini rad etish).
 */
class RoadDetector(context: Context) : AutoCloseable {
    private val detector: ObjectDetector?
    val delegate: String
    private var lastTs = 0L

    // Oldingi kadrlardagi odam qutilari va ularning ichki 8x10 piksel matritsasi (2D qotib turgan rasmni ajratish uchun)
    private data class PatchRecord(
        val box: Box,
        val grid: IntArray, // 8x10 = 80 normalized luma samples inside the person box
        val aspect: Float,
        val liveStreak: Int,
        val ts: Long,
    )
    private val prevPatches = ArrayList<PatchRecord>()

    init {
        var d: ObjectDetector? = null
        var used = "none"
        for (del in listOf(Delegate.CPU)) {
            try {
                val base = BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(del).build()
                val opts = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setScoreThreshold(0.16f) // Ekran/telefon/klaviatura kabi kontekst obyektlarni ham tutish uchun
                    .setMaxResults(25)
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

        // 1-BOSQICH: Kadrda telefon ekrani, monitor, noutbuk, televizor, kitob yoki xona buyumlari borligini tekshirish
        val mediaScreenBoxes = ArrayList<Box>()
        var hasIndoorOrScreenContext = false
        for (d in result.detections()) {
            for (cat in d.categories()) {
                val name = cat.categoryName()
                val score = cat.score()
                if (name in SCREEN_AND_POSTER_CONTAINERS && score >= 0.16f) {
                    val r = d.boundingBox()
                    mediaScreenBoxes += Box(
                        (r.left / w).coerceIn(0f, 1f),
                        (r.top / h).coerceIn(0f, 1f),
                        (r.right / w).coerceIn(0f, 1f),
                        (r.bottom / h).coerceIn(0f, 1f),
                    )
                    hasIndoorOrScreenContext = true
                } else if (name in INDOOR_DESKTOP_OBJECTS && score >= 0.22f) {
                    hasIndoorOrScreenContext = true
                }
            }
        }

        val out = ArrayList<Detection>()
        val nextPatches = ArrayList<PatchRecord>()

        for (d in result.detections()) {
            val cat = d.categories().firstOrNull() ?: continue
            val score = cat.score()
            // Asosiy yo'l obyektlari uchun minimal ishonch chegarasi
            if (score < 0.38f) continue

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
            val aspect = boxHeight / boxWidth.coerceAtLeast(0.01f)
            val candidateBox = Box(normLeft, normTop, normRight, normBottom)

            // 2-BOSQICH: O'Z AVTOMOBILINING KAPOTI / TORPEDOSI FILTRI
            val isOwnCarHoodOrDashboard = (cls == ObjClass.CAR || cls == ObjClass.BUS_TRUCK) &&
                ((boxWidth > 0.65f && normBottom > 0.82f) || (boxWidth > 0.78f && normTop > 0.42f))
            if (isOwnCarHoodOrDashboard) continue

            // 3-BOSQICH: RASM / TELEFON EKRANI / PLAKAT / QO'L FILTRI (Faqat PERSON uchun)
            if (cls == ObjClass.PERSON) {
                // 3.1. Agar kadrda telefon, noutbuk, monitor yoki klaviatura aniqlangan bo'lsa — bu ekrandagi rasm sinovi!
                if (hasIndoorOrScreenContext) continue
                val overlapsScreen = mediaScreenBoxes.any { screen ->
                    candidateBox.iou(screen) > 0.02f ||
                        (candidateBox.cx in (screen.left - 0.08f)..(screen.right + 0.08f) &&
                            normBottom in (screen.top - 0.05f)..(screen.bottom + 0.08f))
                }
                if (overlapsScreen) continue

                // 3.2. Kamera oldida telefon ushlab turgan qo'l/barmoqlar yoki kadr chetidagi ustun:
                // (Screenshot 3 dagi o'ng chekkadagi "Bola 1m" xatosini 100% yo'qotadi)
                val touchesExtremeSideOrBottom =
                    (normBottom > 0.88f && (normLeft < 0.08f || normRight > 0.92f)) ||
                    (normBottom > 0.94f && boxWidth > 0.25f) ||
                    (normRight > 0.96f || normLeft < 0.04f)
                if (touchesExtremeSideOrBottom) continue

                // 3.3. Geometrik 3D Masshtab va Perspektiva qonuni:
                // Telefon ekranidagi rasm yoki kichik plakat kamera oldiga tutilganda (normBottom > 0.58f, ya'ni 1m..3m),
                // rasmdagi odamchaning quti balandligi (boxHeight) haqiqiy 1.5–1.8m odamnikidan ancha kichik bo'ladi!
                // Haqiqiy odam 2 metr masofada (normBottom >= 0.68f) kadr balandligining kamida 58% ini egallaydi (boxHeight >= 0.58f).
                val isMiniaturePhotoScale =
                    (normBottom >= 0.65f && boxHeight < 0.56f) ||
                    (normBottom >= 0.55f && boxHeight < 0.36f) ||
                    (normBottom < 0.43f) || // Balandda devor/ustunga osilgan plakat
                    (aspect < 1.45f) || // Yassi/kvadrat portret rasm yoki qo'l
                    (boxHeight > 0.88f && normTop < 0.05f) // Kameraga juda yaqin tutilgan plakat
                if (isMiniaturePhotoScale) continue

                // 3.4. Piksel darajasida telefon ekrani ramkasi (Bezel / Backlight) va 2D Qotib turgan rasm tekshiruvi:
                val currentGrid = sampleNormalizedPersonGrid(bitmap, normLeft, normTop, normRight, normBottom)
                if (hasPhoneBezelOrFlatScreenBorder(bitmap, normLeft, normTop, normRight, normBottom)) {
                    continue
                }

                // Oldingi kadrdagi shu odam qutisi bilan ichki harakatni (oyoq va tana artikulyatsiyasini) solishtiramiz:
                val prev = prevPatches.maxByOrNull { it.box.iou(candidateBox) }
                val matchedPrev = if (prev != null && prev.box.iou(candidateBox) >= 0.25f) prev else null

                var isArticulatingLiveHuman = false
                var newStreak = 0
                if (matchedPrev != null) {
                    val artic = computeNonRigidArticulation(matchedPrev.grid, currentGrid)
                    val boxShiftX = abs(candidateBox.cx - matchedPrev.box.cx)
                    val boxShiftY = abs(candidateBox.bottom - matchedPrev.box.bottom)
                    val aspectDelta = abs(aspect - matchedPrev.aspect)

                    // 2D rasm (hatto qo'lda qimirlatilsa ham) ichki qismlari (oyoq-qo'li) bir-biriga nisbatan bukilmaydi!
                    // Haqiqiy yurayotgan/tirik odamda esa pastki qism (oyoqlar) yuqori qismga nisbatan artikulyatsiya qiladi (artic >= 11.5f)
                    val hasRealLimbMotion = artic >= 11.5f && (aspectDelta >= 0.025f || boxShiftX >= 0.012f || boxShiftY >= 0.010f)
                    newStreak = if (hasRealLimbMotion) (matchedPrev.liveStreak + 1).coerceAtMost(10) else max(0, matchedPrev.liveStreak - 1)
                    isArticulatingLiveHuman = newStreak >= 3
                }

                nextPatches += PatchRecord(candidateBox, currentGrid, aspect, newStreak, ts)

                // Agar 2D rasm bo'lsa (isArticulatingLiveHuman == false), uni UMUMAN tanimaymiz va xabar bermaymiz!
                if (!isArticulatingLiveHuman) continue
            }

            out += Detection(
                cls, score,
                candidateBox,
                isLive = true
            )
        }

        prevPatches.clear()
        prevPatches.addAll(nextPatches)
        return out
    }

    /**
     * Odam qutisi ichidan 8x10 (80 nuqta) normallashgan yorug'lik (Luminance) matritsasini oladi.
     * Quti siljisa ham, 2D rasmda quti ichidagi nisbiy nuqtalar o'zgarmasdan qoladi.
     */
    private fun sampleNormalizedPersonGrid(bmp: Bitmap, left: Float, top: Float, right: Float, bottom: Float): IntArray {
        val cols = 8
        val rows = 10
        val grid = IntArray(cols * rows)
        val bw = bmp.width
        val bh = bmp.height
        val w = (right - left).coerceAtLeast(0.02f)
        val h = (bottom - top).coerceAtLeast(0.02f)
        var sum = 0
        for (r in 0 until rows) {
            val ny = top + (r + 0.5f) / rows * h
            val py = (ny * bh).toInt().coerceIn(0, bh - 1)
            for (c in 0 until cols) {
                val nx = left + (c + 0.5f) / cols * w
                val px = (nx * bw).toInt().coerceIn(0, bw - 1)
                val rgb = bmp.getPixel(px, py)
                val luma = ((rgb shr 16 and 0xFF) * 77 + (rgb shr 8 and 0xFF) * 150 + (rgb and 0xFF) * 29) shr 8
                grid[r * cols + c] = luma
                sum += luma
            }
        }
        // O'rtacha yorug'likni ayirib normallashtiramiz (ekran yorug'ligi o'zgarsa ham aldanmasligi uchun)
        val mean = sum / (cols * rows)
        for (i in grid.indices) {
            grid[i] -= mean
        }
        return grid
    }

    /**
     * Yuqori tana (qatorlar 1..4) va pastki oyoqlar (qatorlar 5..9) o'rtasidagi noregid (mustaqil) harakat farqini hisoblaydi.
     * 2D rasmda oyoqlar qotib turgani uchun chap va o'ng oyoq sohalari bir-biriga nisbatan siljimaydi.
     */
    private fun computeNonRigidArticulation(prev: IntArray, cur: IntArray): Float {
        val cols = 8
        var upperDiff = 0f
        var lowerLeftDiff = 0f
        var lowerRightDiff = 0f

        for (r in 1..4) {
            for (c in 1..6) {
                upperDiff += abs(cur[r * cols + c] - prev[r * cols + c])
            }
        }
        upperDiff /= 24f

        for (r in 5..9) {
            for (c in 1..3) {
                lowerLeftDiff += abs(cur[r * cols + c] - prev[r * cols + c])
            }
            for (c in 4..6) {
                lowerRightDiff += abs(cur[r * cols + c] - prev[r * cols + c])
            }
        }
        lowerLeftDiff /= 15f
        lowerRightDiff /= 15f

        // Oyoqlarning bir-biriga va tanaga nisbatan nomutanosib (qadam tashlash) o'zgarishi:
        val legAsymmetry = abs(lowerLeftDiff - lowerRightDiff)
        val bodyToLegDelta = abs((lowerLeftDiff + lowerRightDiff) * 0.5f - upperDiff)
        return legAsymmetry * 1.4f + bodyToLegDelta * 1.2f
    }

    /**
     * Telefon ekrani yoki ramkali plakat ichidagi rasmni aniqlash:
     * Telefon ekranining chap/o'ng chetlarida keskin qora ramka (bezel) yoki yaltiroq ekran chizig'i bo'ladi.
     */
    private fun hasPhoneBezelOrFlatScreenBorder(bmp: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val bw = bmp.width
        val bh = bmp.height
        val boxW = right - left
        // Odam qutisining chap va o'ng tashqi tomonida (telefon chetida) vertikal chiziq bo'ylab qora bezel tekshiruvi
        val checkLeftX = ((left - boxW * 0.35f) * bw).toInt()
        val checkRightX = ((right + boxW * 0.35f) * bw).toInt()
        if (checkLeftX < 4 || checkRightX >= bw - 4) return false

        var darkBezelCount = 0
        val samples = 8
        for (i in 0 until samples) {
            val py = ((top + (bottom - top) * (i + 0.5f) / samples) * bh).toInt().coerceIn(0, bh - 1)
            val rgbL = bmp.getPixel(checkLeftX, py)
            val rgbR = bmp.getPixel(checkRightX, py)
            val lumaL = ((rgbL shr 16 and 0xFF) + (rgbL shr 8 and 0xFF) + (rgbL and 0xFF)) / 3
            val lumaR = ((rgbR shr 16 and 0xFF) + (rgbR shr 8 and 0xFF) + (rgbR and 0xFF)) / 3
            if (lumaL < 28 || lumaR < 28) darkBezelCount++
        }
        return darkBezelCount >= 7
    }

    override fun close() { runCatching { detector?.close() } }

    companion object {
        const val MODEL = "efficientdet_lite0.tflite"

        private val SCREEN_AND_POSTER_CONTAINERS = setOf(
            "cell phone", "tv", "laptop", "book", "remote", "clock"
        )
        private val INDOOR_DESKTOP_OBJECTS = setOf(
            "keyboard", "mouse", "chair", "couch", "bed", "dining table",
            "refrigerator", "microwave", "oven", "sink", "toilet", "vase", "cup"
        )
    }
}
