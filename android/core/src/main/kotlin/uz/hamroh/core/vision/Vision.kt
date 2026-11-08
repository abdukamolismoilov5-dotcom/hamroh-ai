package uz.hamroh.core.vision

import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** Aniqlangan obyekt sinfi (FR-4.1). */
enum class ObjClass { PERSON, BIKE, CAR, BUS_TRUCK;
    val isVehicle get() = this == CAR || this == BUS_TRUCK
}

/** Normallashgan quti: 0..1, (left, top) — yuqori chap burchak. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2f
    fun iou(o: Box): Float {
        val ix = max(0f, min(right, o.right) - max(left, o.left))
        val iy = max(0f, min(bottom, o.bottom) - max(top, o.top))
        val inter = ix * iy
        val union = width * height + o.width * o.height - inter
        return if (union <= 0f) 0f else inter / union
    }
}

data class Detection(
    val cls: ObjClass,
    val confidence: Float,
    val box: Box,
    val isLive: Boolean = true,
)

/**
 * FR-4.3: monokulyar yer tekisligi geometriyasi.
 * [cameraHeightM] — kameraning yerdan balandligi; [pitchDownRad] — optik o'qning gorizontdan pastga og'ishi;
 * [vFovDeg]/[hFovDeg] — kadrning vertikal/gorizontal ko'rish burchagi (tahlil qilinadigan yo'nalishda).
 */
data class CameraGeometry(
    val cameraHeightM: Double = 1.25,
    val pitchDownRad: Double = 0.0,
    val vFovDeg: Double = 48.0,
    val hFovDeg: Double = 66.0,
) {
    private val fy = 0.5 / tan(Math.toRadians(vFovDeg / 2))
    private val fx = 0.5 / tan(Math.toRadians(hFovDeg / 2))

    /** Gorizont chizig'ining normallashgan y koordinatasi. */
    val horizonY get() = 0.5 - fy * tan(pitchDownRad)

    /** Kadrdagi y (yer bilan tegish nuqtasi) bo'yicha masofa, m. Gorizontdan yuqorida — null. */
    fun groundDistanceM(yBottom: Float): Double? {
        val alpha = atan((yBottom - 0.5) / fy)
        val down = pitchDownRad + alpha
        if (down <= Math.toRadians(0.3)) return null
        return cameraHeightM / tan(down)
    }

    /**
     * Agar kamera qo'lda tutilsa yoki odamning faqat yuqori tanasi kadrga tushsa (yBottom gorizontga yaqin bo'lsa),
     * quti balandligiga asoslangan zaxira masofa baholagich.
     */
    fun estimateDistanceM(cls: ObjClass, box: Box): Double {
        val ground = groundDistanceM(box.bottom)
        val refHeightM = when (cls) {
            ObjClass.PERSON -> 1.62
            ObjClass.BIKE -> 1.40
            ObjClass.CAR -> 1.50
            ObjClass.BUS_TRUCK -> 2.80
        }
        val boxBasedDist = (refHeightM * fy / box.height.coerceAtLeast(0.06f)).coerceIn(1.0, 60.0)
        if (ground == null) return boxBasedDist
        val apparentH = objectHeightM(box, ground)
        // Agar odamning oyoqlari kadr pastidan kesilgan bo'lsa (masalan, xonada stol ortida tursa),
        // yer tekisligi formulasi masofani haddan tashqari uzoq (>2.4m bo'y) deb hisoblaydi — bu holda quti o'lchamidan olamiz
        if (cls == ObjClass.PERSON && apparentH > 2.40) {
            return boxBasedDist
        }
        return ground
    }

    /** Normallashgan x bo'yicha yon siljish (o'ng = musbat), m. */
    fun lateralM(x: Float, distanceM: Double): Double = (x - 0.5) / fx * sqrt(distanceM * distanceM + cameraHeightM * cameraHeightM)

    /** Quti balandligidan obyektning taxminiy haqiqiy bo'yi, m. */
    fun objectHeightM(box: Box, distanceM: Double): Double {
        val slant = sqrt(distanceM * distanceM + cameraHeightM * cameraHeightM)
        return slant * (box.height / fy) * kotlin.math.cos(pitchDownRad)
    }

    /** Normallashgan x tezligini (1/s) metr/s ga o'tkazish. */
    fun lateralSpeedMs(vxNorm: Double, distanceM: Double): Double = vxNorm / fx * distanceM

    companion object {
        /**
         * FR-4.3 avtomatik kalibrlash: Android gravitatsiya sensori (TYPE_GRAVITY, "yuqoriga" vektor) bo'yicha
         * orqa kamera optik o'qining pastga og'ishi. Telefon tik turganda (z ≈ 0) og'ish 0 ga teng.
         */
        fun pitchFromGravity(gx: Double, gy: Double, gz: Double): Double {
            val n = sqrt(gx * gx + gy * gy + gz * gz)
            if (n < 1e-6) return 0.0
            return asin((gz / n).coerceIn(-1.0, 1.0))
        }
    }
}

/** Kuzatilayotgan obyekt. */
class Track(
    val id: Int,
    var cls: ObjClass,
    var box: Box,
    var confidence: Float,
    var lastSeenMs: Long,
    var isLive: Boolean = true,
) {
    var hits = 1
    var vx = 0.0          // normallashgan x tezligi, 1/s
    var distanceM: Double? = null
    var dDot = 0.0        // masofa o'zgarish tezligi, m/s (manfiy = yaqinlashmoqda)
    var distanceSamples = 0
    var staticCount = 0
    var prevBox: Box? = null
    internal var lastUpdateMs = lastSeenMs
}

/**
 * FR-4.2: IoU bo'yicha moslash + alfa-beta filtr (Kalmanning barqaror holatdagi ko'rinishi) bilan kuzatuvchi.
 * Inferenslar orasida [predict] qutilarni tezlik bo'yicha suradi.
 */
class Tracker(private val geometry: () -> CameraGeometry) {
    private val tracks = ArrayList<Track>()
    private var nextId = 1
    private val alpha = 0.5
    private val beta = 0.3

    fun active(): List<Track> = tracks

    fun update(timeMs: Long, detections: List<Detection>): List<Track> {
        val unmatched = detections.toMutableList()
        // ochko'z moslash: eng yuqori IoU birinchi
        val pairs = ArrayList<Triple<Track, Detection, Float>>()
        for (t in tracks) for (d in detections) {
            if (t.cls.isVehicle != d.cls.isVehicle) continue
            val iou = t.box.iou(d.box)
            if (iou >= Thresholds.TRACK_MIN_IOU) pairs += Triple(t, d, iou)
        }
        pairs.sortByDescending { it.third }
        val usedT = HashSet<Track>()
        val usedD = HashSet<Detection>()
        for ((t, d, _) in pairs) {
            if (t in usedT || d in usedD) continue
            usedT += t; usedD += d
            correct(t, d, timeMs)
        }
        unmatched.removeAll(usedD)
        for (d in unmatched) {
            val t = Track(nextId++, d.cls, d.box, d.confidence, timeMs, isLive = d.isLive)
            t.distanceM = geometry().estimateDistanceM(d.cls, d.box)
            if (t.distanceM != null) t.distanceSamples = 1
            t.prevBox = d.box
            tracks += t
        }
        tracks.removeAll { timeMs - it.lastSeenMs > Thresholds.TRACK_MAX_AGE_MS }
        return tracks
    }

    private fun correct(t: Track, d: Detection, timeMs: Long) {
        val dt = ((timeMs - t.lastUpdateMs) / 1000.0).coerceAtLeast(1e-3)
        // x tezligi
        val predX = t.box.cx + t.vx * dt
        val rx = d.box.cx - predX
        val newCx = predX + alpha * rx
        t.vx += beta * rx / dt
        val half = d.box.width / 2f
        t.box = Box((newCx - half).toFloat(), d.box.top, (newCx + half).toFloat(), d.box.bottom)

        val prev = t.prevBox
        if (prev != null) {
            val dx = abs(d.box.cx - prev.cx)
            val dy = abs(d.box.bottom - prev.bottom)
            if (dx < 0.003f && dy < 0.003f) {
                t.staticCount = minOf(20, t.staticCount + 1)
            } else {
                t.staticCount = maxOf(0, t.staticCount - 1)
            }
        }
        t.prevBox = d.box
        t.isLive = d.isLive

        // masofa
        val meas = geometry().estimateDistanceM(d.cls, d.box)
        val prevDist = t.distanceM
        if (prevDist == null) {
            t.distanceM = meas
            t.distanceSamples = 1
        } else {
            val pred = prevDist + t.dDot * dt
            val r = meas - pred
            t.distanceM = pred + alpha * r
            t.dDot += beta * r / dt
            t.distanceSamples++
        }
        t.cls = d.cls
        t.confidence = 0.6f * t.confidence + 0.4f * d.confidence
        t.hits++
        t.lastSeenMs = timeMs
        t.lastUpdateMs = timeMs
    }

    fun clear() { tracks.clear() }
}

/** Bitta obyekt bo'yicha hisoblangan xavf (UI chizishi uchun ham). */
data class TrackRisk(
    val track: Track,
    val distanceM: Double,
    val lateralM: Double,
    val ttcS: Double?,
    val inCorridor: Boolean,
    val entering: Boolean,
    val likelyChild: Boolean,
    val isLive: Boolean = true,
)

/**
 * FR-4.4–4.8: kuzatilayotgan obyektlar bo'yicha piyoda, oldingi mashina va "yo'l ochiq" ogohlantirishlari.
 */
class VisionRiskEvaluator {
    private val halfCorridor = Thresholds.CAR_WIDTH_M / 2 + Thresholds.CORRIDOR_MARGIN_M
    private var leadMovingSince: Long? = null
    private var leadAlertedThisStop = false

    var lastRisks: List<TrackRisk> = emptyList()
        private set

    fun evaluate(
        timeMs: Long,
        tracks: List<Track>,
        geometry: CameraGeometry,
        egoSpeedMps: Double,
        lookaheadM: Double,
        inSchoolZone: Boolean,
    ): List<Alert> {
        val out = ArrayList<Alert>()
        val risks = ArrayList<TrackRisk>()
        val egoKmh = egoSpeedMps * 3.6
        val minConf = if (inSchoolZone) Thresholds.VISION_MIN_CONFIDENCE_SCHOOL else Thresholds.VISION_MIN_CONFIDENCE
        var pedestrianAlert: Alert? = null
        var fcwAlert: Alert? = null
        var leadTrack: TrackRisk? = null

        for (t in tracks) {
            val d = t.distanceM ?: continue
            val x = geometry.lateralM(t.box.cx, d)
            val vxM = geometry.lateralSpeedMs(t.vx, d)
            val inCorr = abs(x) <= halfCorridor
            val futureX = x + vxM * Thresholds.CORRIDOR_ENTRY_HORIZON_S
            val entering = !inCorr && abs(futureX) <= halfCorridor && abs(futureX) < abs(x)
            val relClosing = if (t.distanceSamples >= 3) -t.dDot else 0.0
            val closing = if (t.cls.isVehicle) relClosing else max(relClosing, egoSpeedMps)
            val ttc = if (closing > 0.3) d / closing else null
            val height = geometry.objectHeightM(t.box, d)

            val child = t.cls == ObjClass.PERSON && d >= 2.2 && height in 0.65..Thresholds.CHILD_HEIGHT_M
            val r = TrackRisk(t, d, x, ttc, inCorr, entering, child, isLive = t.isLive)
            risks += r

            if (t.hits < Thresholds.VISION_MIN_HITS || t.confidence < minConf) continue
            if (d > lookaheadM) continue

            if (t.cls == ObjClass.PERSON || t.cls == ObjClass.BIKE) {
                val movingDanger = (inCorr || entering) && ((ttc != null && ttc < Thresholds.PEDESTRIAN_TTC_S) || d <= 15.0)
                val stationaryCloseDemo = (inCorr || entering) && (d <= 6.5 || closing > 0.25 || abs(vxM) > 0.25)
                val shouldAlert = if (egoKmh >= 3.0) movingDanger else stationaryCloseDemo
                if (shouldAlert) {
                    val level = if (t.confidence >= Thresholds.VISION_VOICE_CONFIDENCE) AlertLevel.URGENT else AlertLevel.VOICE
                    val a = Alert(
                        if (child) AlertType.PEDESTRIAN_CHILD else AlertType.PEDESTRIAN,
                        level, timeMs, if (child) "ped_child" else "ped",
                        mapOf("distance" to d.toInt().toString(), "ttc" to String.format(java.util.Locale.US, "%.1f", ttc ?: (d / 4.0))),
                        t.confidence,
                    )
                    if (pedestrianAlert == null || (d < (pedestrianAlert.args["distance"]?.toDoubleOrNull() ?: 99.0))) pedestrianAlert = a
                }
            } else if (inCorr) {
                if (leadTrack == null || d < leadTrack.distanceM) leadTrack = r
                val closeVehicleDanger = (ttc != null && ttc < Thresholds.FCW_TTC_S) || (d <= 11.5 && egoKmh >= 4.0) || (d <= 6.5 && closing > 0.25)
                if (closeVehicleDanger) {
                    fcwAlert = Alert(
                        AlertType.FORWARD_COLLISION,
                        if (t.confidence >= Thresholds.VISION_VOICE_CONFIDENCE) AlertLevel.URGENT else AlertLevel.VOICE,
                        timeMs, "fcw", mapOf("distance" to d.toInt().toString()), t.confidence,
                    )
                }
            }
        }
        pedestrianAlert?.let { out += it }
        fcwAlert?.let { out += it }

        // FR-4.8: "yo'l ochiq"
        if (egoKmh < 1.0) {
            val lead = leadTrack
            if (lead != null && lead.track.distanceSamples >= 3 && lead.track.dDot > 0.5 && lead.distanceM < 25) {
                val since = leadMovingSince ?: timeMs.also { leadMovingSince = it }
                if (!leadAlertedThisStop && timeMs - since >= Thresholds.LEAD_MOVED_MS) {
                    leadAlertedThisStop = true
                    out += Alert(AlertType.LEAD_MOVED, AlertLevel.TICK, timeMs, "lead_moved")
                }
            } else leadMovingSince = null
        } else if (egoKmh > 5.0) {
            leadMovingSince = null; leadAlertedThisStop = false
        }
        lastRisks = risks
        return out
    }
}
