package uz.hamroh.core.model

import uz.hamroh.core.geo.LatLon

/** GPS o'lchovi. [timeMs] — monoton vaqt (yoki epoch), [speedMps] — m/s, [bearingDeg] — 0..360 yoki null. */
data class LocationFix(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Double,
    val bearingDeg: Double?,
    val accuracyM: Double = 5.0,
) {
    val point get() = LatLon(lat, lon)
    val speedKmh get() = speedMps * 3.6
}

/**
 * Inersial o'lchov. Android qatlami xom akselerometrdan quyidagilarni hisoblab beradi:
 * [totalG] — umumiy tezlanish moduli g birligida (tinch holatda ~1.0),
 * [verticalMs2] — gravitatsiya yo'nalishidagi chiziqli tezlanish (gravitatsiyasiz), m/s².
 */
data class ImuSample(
    val timeMs: Long,
    val totalG: Double,
    val verticalMs2: Double,
)

enum class PoiType { SCHOOL, KINDERGARTEN, SPEED_CAMERA, CROSSWALK, SPEED_BUMP, POTHOLE, GAS_STATION }

/** Xaritadagi obyekt. [speedLimitKmh] — faqat radar uchun; [directionDeg] — radar qaragan yo'nalish (bo'lsa). */
data class Poi(
    val id: String,
    val type: PoiType,
    val lat: Double,
    val lon: Double,
    val speedLimitKmh: Int? = null,
    val directionDeg: Double? = null,
    val name: String? = null,
) {
    val point get() = LatLon(lat, lon)
}

/** Ogohlantirish darajasi (FR-4.6, FR-2.4). */
enum class AlertLevel { TICK, VOICE, URGENT }

/**
 * Ogohlantirish turlari. [priority] — kichik son = yuqori ustuvorlik (FR-10.2).
 * [voiceKey] — audio fayl / matn kaliti (uz, ru, en resurslarida bir xil).
 */
enum class AlertType(val priority: Int, val voiceKey: String) {
    SOS_COUNTDOWN(0, "sos_countdown"),
    PEDESTRIAN(1, "pedestrian_ahead"),
    PEDESTRIAN_CHILD(1, "child_ahead"),
    FORWARD_COLLISION(2, "forward_collision"),
    FATIGUE(3, "fatigue_rest"),
    SPEEDING(4, "speeding"),
    SCHOOL_ZONE(5, "school_zone"),
    KINDERGARTEN_ZONE(5, "kindergarten_zone"),
    SPEED_CAMERA(6, "speed_camera"),
    CROSSWALK(7, "crosswalk"),
    SPEED_BUMP(8, "speed_bump"),
    POTHOLE(8, "pothole"),
    LEAD_MOVED(9, "lead_moved"),
    BREAK_SUGGESTION(9, "break_suggestion"),
    PHONE_DISTRACTION(9, "phone_distraction"),
    THERMAL(9, "thermal_camera_off"),
}

/**
 * Ogohlantirish. [dedupKey] — bir xil kalitli xabar [uz.hamroh.core.Thresholds.ALERT_DEDUP_MS] ichida takrorlanmaydi.
 * [args] — ovoz/matnga qo'shiladigan qiymatlar (masalan, tezlik cheklovi).
 */
data class Alert(
    val type: AlertType,
    val level: AlertLevel,
    val timeMs: Long,
    val dedupKey: String = type.name,
    val args: Map<String, String> = emptyMap(),
    val confidence: Float? = null,
)
