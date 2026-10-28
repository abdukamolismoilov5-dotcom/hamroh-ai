package uz.hamroh.core.crowd

import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.ImuSample
import kotlin.math.abs

/** Jamoaviy xaritaga yuboriladigan avtomatik hodisa turi. */
enum class RoadEventType { POTHOLE, HARD_BRAKE }

/** Aniqlangan hodisa. Koordinatani Android qatlami eng yaqin GPS nuqtasidan qo'shadi. */
data class RoadEvent(val type: RoadEventType, val timeMs: Long, val severity: Double)

/**
 * FR-3.1: chuqur / notekislikni aniqlash.
 * [Thresholds.POTHOLE_WINDOW_MS] oynada vertikal tezlanish ham +[Thresholds.POTHOLE_ACCEL_MS2] dan, ham
 * −[Thresholds.POTHOLE_ACCEL_MS2] dan oshsa (keskin tushish va ko'tarilish), tezlik yetarli bo'lsa — hodisa.
 * Severity = oynadagi cho'qqidan-cho'qqigacha qiymat (m/s²).
 */
class PotholeDetector {
    private val window = ArrayDeque<ImuSample>()
    private var lastEventMs = Long.MIN_VALUE / 2

    fun onSample(s: ImuSample, speedKmh: Double): RoadEvent? {
        window.addLast(s)
        while (window.isNotEmpty() && s.timeMs - window.first().timeMs > Thresholds.POTHOLE_WINDOW_MS) window.removeFirst()
        if (speedKmh < Thresholds.POTHOLE_MIN_SPEED_KMH) return null
        if (s.timeMs - lastEventMs < Thresholds.POTHOLE_REFRACTORY_MS) return null
        var mx = Double.NEGATIVE_INFINITY
        var mn = Double.POSITIVE_INFINITY
        for (w in window) { if (w.verticalMs2 > mx) mx = w.verticalMs2; if (w.verticalMs2 < mn) mn = w.verticalMs2 }
        if (mx >= Thresholds.POTHOLE_ACCEL_MS2 && mn <= -Thresholds.POTHOLE_ACCEL_MS2) {
            lastEventMs = s.timeMs
            window.clear()
            return RoadEvent(RoadEventType.POTHOLE, s.timeMs, mx - mn)
        }
        return null
    }
}

/**
 * FR-3.2: qattiq tormoz. Telefon yo'nalishi noma'lum bo'lgani uchun bo'ylama sekinlashish GPS tezligi
 * hosilasidan olinadi: sekinlashish ≥ [Thresholds.HARD_BRAKE_MS2] kamida [Thresholds.HARD_BRAKE_MIN_MS] davom etsa.
 */
class HardBrakeDetector {
    private var prevT: Long? = null
    private var prevV = 0.0
    private var brakingSince: Long? = null
    private var peak = 0.0
    private var fired = false

    fun onSpeed(timeMs: Long, speedMps: Double): RoadEvent? {
        val pt = prevT
        prevT = timeMs
        val pv = prevV
        prevV = speedMps
        if (pt == null) return null
        val dt = (timeMs - pt) / 1000.0
        if (dt <= 0.0 || dt > 3.0) { brakingSince = null; return null }
        val decel = (pv - speedMps) / dt
        if (decel >= Thresholds.HARD_BRAKE_MS2) {
            val since = brakingSince ?: pt.also { brakingSince = it; peak = 0.0; fired = false }
            if (decel > peak) peak = decel
            if (!fired && timeMs - since >= Thresholds.HARD_BRAKE_MIN_MS) {
                fired = true
                return RoadEvent(RoadEventType.HARD_BRAKE, timeMs, peak)
            }
        } else {
            brakingSince = null
        }
        return null
    }
}

/** Yordamchi: tezlanish g dan qancha og'ganini qaytaradi (m/s²). */
internal fun ImuSample.deviationFromGravityMs2() = abs(totalG - 1.0) * Thresholds.G
