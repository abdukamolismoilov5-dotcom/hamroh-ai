package uz.hamroh.core.safety

import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.ImuSample
import kotlin.math.abs

/**
 * FR-6.2 + FR-7: zarba, videoni himoyalash va SOS holat mashinasi.
 *
 * Holatlar: MONITORING → (zarba ≥ 4g) IMPACT_WAIT → (10 s harakatsizlik) COUNTDOWN → (30 s javobsiz) TRIGGERED.
 * Haydovchi [cancel] chaqirsa yoki harakat davom etsa, MONITORING ga qaytadi.
 * FR-7.4: erkin tushishdan keyingi zarba past tezlikda bo'lsa — telefon tushgan, SOS emas.
 */
class CrashSosMachine {
    enum class State { MONITORING, IMPACT_WAIT, COUNTDOWN, TRIGGERED }

    sealed interface Event {
        /** FR-6.2: videoregistrator klipini himoyalash kerak. */
        data class ProtectClip(val timeMs: Long, val peakG: Double) : Event
        /** FR-7.1: haydovchiga 30 soniyalik hisob ko'rsatilsin. */
        data class CountdownStarted(val timeMs: Long, val peakG: Double) : Event
        /** FR-7.2: oilaga xabar yuborilsin. */
        data class SosTriggered(val timeMs: Long, val peakG: Double) : Event
        data class Cancelled(val timeMs: Long) : Event
        /** FR-7.4: telefon tushib ketdi (SOS emas). */
        data class PhoneDropped(val timeMs: Long) : Event
    }

    var state = State.MONITORING
        private set
    private var impactAt = 0L
    private var peakG = 0.0
    private var stillSince: Long? = null
    private var countdownAt = 0L
    private var freeFallSince: Long? = null
    private var lastFreeFallEnd = Long.MIN_VALUE / 2
    private var lastProtectAt = Long.MIN_VALUE / 2

    /** Har bir IMU o'lchovida chaqiriladi. [speedKmh] — so'nggi GPS tezligi. */
    fun onSample(s: ImuSample, speedKmh: Double): List<Event> {
        val out = ArrayList<Event>(1)
        // erkin tushishni kuzatish
        if (s.totalG < Thresholds.FREE_FALL_G) {
            if (freeFallSince == null) freeFallSince = s.timeMs
        } else {
            val ff = freeFallSince
            if (ff != null && s.timeMs - ff >= Thresholds.FREE_FALL_MIN_MS) lastFreeFallEnd = s.timeMs
            freeFallSince = null
        }

        if (s.totalG >= Thresholds.DASHCAM_PROTECT_G && s.timeMs - lastProtectAt > 5_000) {
            val dropped = s.timeMs - lastFreeFallEnd <= Thresholds.FREE_FALL_TO_IMPACT_MS && speedKmh < Thresholds.DROP_MAX_SPEED_KMH
            if (!dropped) {
                lastProtectAt = s.timeMs
                out += Event.ProtectClip(s.timeMs, s.totalG)
            }
        }

        when (state) {
            State.MONITORING -> if (s.totalG >= Thresholds.SOS_IMPACT_G) {
                val dropped = s.timeMs - lastFreeFallEnd <= Thresholds.FREE_FALL_TO_IMPACT_MS && speedKmh < Thresholds.DROP_MAX_SPEED_KMH
                if (dropped) {
                    out += Event.PhoneDropped(s.timeMs)
                } else {
                    state = State.IMPACT_WAIT; impactAt = s.timeMs; peakG = s.totalG; stillSince = null
                }
            }
            State.IMPACT_WAIT -> {
                if (s.totalG > peakG) peakG = s.totalG
                val still = speedKmh < Thresholds.STILL_SPEED_KMH &&
                    abs(s.totalG - 1.0) * Thresholds.G < Thresholds.STILL_ACCEL_DEV_MS2
                if (still) {
                    val since = stillSince ?: s.timeMs.also { stillSince = it }
                    if (s.timeMs - since >= Thresholds.SOS_STILL_MS) {
                        state = State.COUNTDOWN; countdownAt = s.timeMs
                        out += Event.CountdownStarted(s.timeMs, peakG)
                    }
                } else {
                    stillSince = null
                    // Zarbadan keyin mashina normal harakatni davom ettirsa (60 s) — avariya emas.
                    if (speedKmh > Thresholds.AUTOSTART_SPEED_KMH && s.timeMs - impactAt > 60_000) state = State.MONITORING
                }
            }
            State.COUNTDOWN -> if (s.timeMs - countdownAt >= Thresholds.SOS_COUNTDOWN_MS) {
                state = State.TRIGGERED
                out += Event.SosTriggered(s.timeMs, peakG)
            }
            State.TRIGGERED -> Unit
        }
        return out
    }

    /** IMU kelmay qolganda ham hisob ishlashi uchun vaqt bo'yicha chaqiriladi. */
    fun tick(nowMs: Long): Event? {
        if (state == State.COUNTDOWN && nowMs - countdownAt >= Thresholds.SOS_COUNTDOWN_MS) {
            state = State.TRIGGERED
            return Event.SosTriggered(nowMs, peakG)
        }
        return null
    }

    /** Hisob qolgan vaqti, ms (COUNTDOWN holatida). */
    fun remainingMs(nowMs: Long): Long =
        if (state == State.COUNTDOWN) (Thresholds.SOS_COUNTDOWN_MS - (nowMs - countdownAt)).coerceAtLeast(0) else 0

    /** FR-7.1: "Men yaxshiman". */
    fun cancel(nowMs: Long): Event {
        state = State.MONITORING; stillSince = null
        return Event.Cancelled(nowMs)
    }

    /** SOS yuborilgandan keyin qayta kuzatishga o'tish. */
    fun reset() { state = State.MONITORING; stillSince = null; peakG = 0.0 }
}
