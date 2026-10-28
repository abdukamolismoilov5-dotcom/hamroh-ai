package uz.hamroh.core.alerts

import uz.hamroh.core.Thresholds
import uz.hamroh.core.drive.LookAhead
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType
import uz.hamroh.core.model.LocationFix
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType

/** Kun vaqti oralig'i, daqiqalarda (0..1439). */
data class MinuteRange(val fromMin: Int, val toMin: Int) {
    fun contains(minuteOfDay: Int) = minuteOfDay in fromMin until toMin
}

/**
 * FR-2.3: maktab hududining kuchaytirilgan rejim jadvali.
 * [workdays] — ISO hafta kunlari (1 = dushanba ... 7 = yakshanba).
 */
data class SchoolSchedule(
    val ranges: List<MinuteRange> = listOf(
        MinuteRange(7 * 60 + 30, 9 * 60),
        MinuteRange(11 * 60 + 30, 13 * 60 + 30),
        MinuteRange(16 * 60 + 30, 18 * 60),
    ),
    val workdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6),
) {
    fun isActive(isoDayOfWeek: Int, minuteOfDay: Int) =
        isoDayOfWeek in workdays && ranges.any { it.contains(minuteOfDay) }
}

/** Mahalliy vaqt ma'lumoti (Android qatlami [java.time] dan beradi; yadro vaqt zonasiga bog'liq emas). */
data class LocalClock(val isoDayOfWeek: Int, val minuteOfDay: Int)

/** Hozir faol bo'lgan hudud holati (UI uchun). */
data class ZoneStatus(val inSchoolZone: Boolean, val enhanced: Boolean, val speedLimitKmh: Int?)

/**
 * FR-2.1–2.4: xaritadagi obyektlar bo'yicha ogohlantirishlar.
 * Har bir POI uchun bir marta ogohlantiradi ([Thresholds.POI_REPEAT_MS] ichida takrorlamaydi).
 */
class PoiAlertEvaluator(
    private val schedule: SchoolSchedule = SchoolSchedule(),
) {
    private val warned = HashMap<String, Long>()
    private var activeLimit: Int? = null
    private var activeLimitPoi: String? = null
    private var lastSpeedingAlert = 0L

    var zone = ZoneStatus(false, false, null)
        private set

    fun evaluate(fix: LocationFix, speedMps: Double, nearby: List<Poi>, clock: LocalClock): List<Alert> {
        val out = ArrayList<Alert>()
        val here = fix.point
        val heading = fix.bearingDeg
        // GPS 1 Hz: bir soniyalik zaxira qo'shiladi, shunda ogohlantirish kamida 8 s oldin beriladi.
        val warnDist = LookAhead.poiWarnDistanceM(speedMps) + speedMps
        val speedKmh = speedMps * 3.6
        val enhancedTime = schedule.isActive(clock.isoDayOfWeek, clock.minuteOfDay)
        var inSchool = false

        for (poi in nearby) {
            val d = Geo.distanceM(here, poi.point)
            val isZone = poi.type == PoiType.SCHOOL || poi.type == PoiType.KINDERGARTEN
            if (isZone && d <= Thresholds.SCHOOL_ZONE_RADIUS_M) inSchool = true
            if (poi.type == PoiType.GAS_STATION) continue
            if (d > warnDist + (if (isZone) Thresholds.SCHOOL_ZONE_RADIUS_M else 0.0)) continue
            if (!isAhead(fix, heading, poi, d, isZone)) continue
            if (poi.type == PoiType.SPEED_CAMERA && poi.directionDeg != null && heading != null &&
                Geo.angleDiffDeg(poi.directionDeg, heading) > 60.0 && Geo.angleDiffDeg(poi.directionDeg, (heading + 180) % 360) > 60.0
            ) continue
            val last = warned[poi.id]
            if (last != null && fix.timeMs - last < Thresholds.POI_REPEAT_MS) continue
            if (speedKmh < 5.0) continue
            warned[poi.id] = fix.timeMs
            out += when (poi.type) {
                PoiType.SCHOOL -> Alert(
                    AlertType.SCHOOL_ZONE,
                    if (enhancedTime) AlertLevel.URGENT else AlertLevel.VOICE,
                    fix.timeMs, "poi:${poi.id}",
                    mapOf("enhanced" to enhancedTime.toString()),
                )
                PoiType.KINDERGARTEN -> Alert(
                    AlertType.KINDERGARTEN_ZONE,
                    if (enhancedTime) AlertLevel.URGENT else AlertLevel.VOICE,
                    fix.timeMs, "poi:${poi.id}",
                    mapOf("enhanced" to enhancedTime.toString()),
                )
                PoiType.SPEED_CAMERA -> {
                    poi.speedLimitKmh?.let { activeLimit = it; activeLimitPoi = poi.id }
                    Alert(
                        AlertType.SPEED_CAMERA, AlertLevel.VOICE, fix.timeMs, "poi:${poi.id}",
                        buildMap {
                            put("distance", (Math.round(d / 50.0) * 50).toString())
                            poi.speedLimitKmh?.let { put("limit", it.toString()) }
                        },
                    )
                }
                PoiType.CROSSWALK -> Alert(AlertType.CROSSWALK, AlertLevel.VOICE, fix.timeMs, "poi:${poi.id}")
                PoiType.SPEED_BUMP -> Alert(AlertType.SPEED_BUMP, AlertLevel.VOICE, fix.timeMs, "poi:${poi.id}")
                PoiType.POTHOLE -> Alert(AlertType.POTHOLE, AlertLevel.VOICE, fix.timeMs, "poi:${poi.id}")
                PoiType.GAS_STATION -> continue
            }
        }

        // Radar o'tib ketilgandan keyin uning cheklovi bekor bo'ladi.
        activeLimitPoi?.let { id ->
            val poi = nearby.firstOrNull { it.id == id }
            if (poi == null || Geo.distanceM(here, poi.point) > warnDist + 50 && !isAhead(fix, heading, poi, Geo.distanceM(here, poi.point), false)) {
                activeLimit = null; activeLimitPoi = null
            }
        }

        val limit = activeLimit ?: if (inSchool && enhancedTime) 40 else null
        zone = ZoneStatus(inSchool, inSchool && enhancedTime, limit)

        // FR-2.4: tezlik oshishi
        if (limit != null) {
            val over = speedKmh - limit
            if (over > Thresholds.SPEEDING_SILENT_KMH && fix.timeMs - lastSpeedingAlert > Thresholds.ALERT_DEDUP_MS) {
                lastSpeedingAlert = fix.timeMs
                out += Alert(
                    AlertType.SPEEDING,
                    AlertLevel.VOICE,
                    fix.timeMs, "speeding:${activeLimitPoi ?: "school"}",
                    mapOf("limit" to limit.toString(), "speed" to speedKmh.toInt().toString()),
                )
            }
        }
        return out
    }

    private fun isAhead(fix: LocationFix, heading: Double?, poi: Poi, d: Double, isZone: Boolean): Boolean {
        if (isZone && d <= Thresholds.SCHOOL_ZONE_RADIUS_M) return true
        if (heading == null) return d < 60.0
        val b = Geo.bearingDeg(fix.point, poi.point)
        return Geo.angleDiffDeg(b, heading) <= Thresholds.POI_SECTOR_DEG
    }

    fun reset() { warned.clear(); activeLimit = null; activeLimitPoi = null }
}
