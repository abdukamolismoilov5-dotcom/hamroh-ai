package uz.hamroh.core.care

import uz.hamroh.core.Thresholds

/** Hujjat turlari (FR-9.2). */
enum class DocKind { OSAGO, INSPECTION, GAS_CERT, PROXY }

/** Eslatma: [daysLeft] — muddatgacha qolgan kun, [stage] — 30/7/1. */
data class DocReminder(val kind: DocKind, val daysLeft: Long, val stage: Int)

object CarCare {
    /** FR-9.1: keyingi moy almashtirishgacha qolgan km (manfiy = o'tib ketgan). */
    fun oilKmLeft(odometerKm: Double, lastOilKm: Double, intervalKm: Int = Thresholds.OIL_INTERVAL_KM_DEFAULT): Double =
        lastOilKm + intervalKm - odometerKm

    /** Moy eslatmasi kerakmi: 500 km qolganda va o'tib ketganda. [notifiedAt] — oxirgi eslatma berilgan qolgan km. */
    fun oilReminderDue(kmLeft: Double, notifiedAtKmLeft: Double?): Boolean {
        val threshold = if (kmLeft <= 0) 0.0 else 500.0
        if (kmLeft > threshold) return false
        return notifiedAtKmLeft == null || notifiedAtKmLeft > threshold
    }

    /**
     * FR-9.2: hujjat eslatmasi. [daysLeft] — muddatgacha qolgan kun, [lastStage] — avval berilgan eng kichik bosqich
     * (null — hali berilmagan). Qaytaradi: yangi bosqich yoki null.
     */
    fun docStageDue(daysLeft: Long, lastStage: Int?): Int? {
        val stages = Thresholds.REMINDER_STAGES_DAYS
        val due = stages.filter { daysLeft <= it }.minOrNull() ?: return null
        if (lastStage != null && lastStage <= due) return null
        return due
    }
}

/** FR-8.3: haftalik baho uchun yig'ilgan ko'rsatkichlar. */
data class WeekStats(
    val distanceKm: Double,
    val speedingEvents: Int,
    val hardBrakes: Int,
    val nightKm: Double,
    val fatigueAlerts: Int,
    val distractionAlerts: Int,
)

object DriverScore {
    /**
     * 0..100. 100 km ga normallashtirilgan jarimalar:
     * tezlik oshishi 4, qattiq tormoz 3, charchoq 6, chalg'ish 3 ball; tungi haydash ulushi uchun max 10 ball.
     * Masofa 10 km dan kam bo'lsa — baho hisoblanmaydi (null).
     */
    fun compute(s: WeekStats): Int? {
        if (s.distanceKm < 10.0) return null
        val per100 = 100.0 / s.distanceKm
        val penalty = s.speedingEvents * per100 * 4 +
            s.hardBrakes * per100 * 3 +
            s.fatigueAlerts * per100 * 6 +
            s.distractionAlerts * per100 * 3 +
            (s.nightKm / s.distanceKm).coerceIn(0.0, 1.0) * 10
        return (Thresholds.SCORE_MAX - penalty).toInt().coerceIn(0, Thresholds.SCORE_MAX)
    }
}
