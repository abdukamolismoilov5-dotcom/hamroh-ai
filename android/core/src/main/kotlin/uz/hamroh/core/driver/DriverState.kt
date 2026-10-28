package uz.hamroh.core.driver

import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType
import kotlin.math.hypot

/** 2D nuqta (piksel yoki normallashgan koordinata). */
data class P2(val x: Float, val y: Float)

/**
 * Eye Aspect Ratio (Soukupová & Čech, 2016): EAR = (|p2−p6| + |p3−p5|) / (2·|p1−p4|).
 * Nuqtalar tartibi: p1 — tashqi burchak, p4 — ichki burchak, p2,p3 — yuqori qovoq, p6,p5 — pastki qovoq.
 */
object Ear {
    fun of(p1: P2, p2: P2, p3: P2, p4: P2, p5: P2, p6: P2): Float {
        fun d(a: P2, b: P2) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        val h = d(p1, p4)
        if (h < 1e-6) return 0f
        return ((d(p2, p6) + d(p3, p5)) / (2.0 * h)).toFloat()
    }
}

/**
 * FR-5.1: charchoqni aniqlash. Ikki ko'zning o'rtacha EAR qiymati kadrma-kadr beriladi.
 * Signal: ko'z ≥ 1.5 s uzluksiz yumilgan YOKI 60 s ichida ≥ 3 ta uzoq (≥ 0.5 s) yumilish.
 */
class FatigueDetector {
    private var closedSince: Long? = null
    private var alertedThisClosure = false
    private val longBlinks = ArrayDeque<Long>()

    fun onEar(timeMs: Long, ear: Float?): Alert? {
        if (ear == null) { closedSince = null; return null } // yuz ko'rinmayapti
        if (ear < Thresholds.EAR_CLOSED) {
            val since = closedSince ?: timeMs.also { closedSince = it; alertedThisClosure = false }
            if (!alertedThisClosure && timeMs - since >= Thresholds.EYES_CLOSED_ALERT_MS) {
                alertedThisClosure = true
                return Alert(AlertType.FATIGUE, AlertLevel.URGENT, timeMs, "fatigue", mapOf("reason" to "eyes_closed"))
            }
        } else {
            val since = closedSince
            closedSince = null
            if (since != null && timeMs - since >= Thresholds.LONG_BLINK_MS && !alertedThisClosure) {
                longBlinks.addLast(timeMs)
                while (longBlinks.isNotEmpty() && timeMs - longBlinks.first() > Thresholds.LONG_BLINK_WINDOW_MS) longBlinks.removeFirst()
                if (longBlinks.size >= Thresholds.LONG_BLINKS_IN_WINDOW) {
                    longBlinks.clear()
                    return Alert(AlertType.FATIGUE, AlertLevel.URGENT, timeMs, "fatigue", mapOf("reason" to "long_blinks"))
                }
            }
        }
        return null
    }
}

/** FR-5.3: uzluksiz haydash vaqti; 15 daqiqalik to'xtash hisobni nolga tushiradi. */
class DrivingTimeTracker {
    private var drivingStart: Long? = null
    private var stoppedSince: Long? = null
    private var suggested = false

    fun update(timeMs: Long, moving: Boolean): Alert? {
        if (moving) {
            val stop = stoppedSince
            if (stop != null && timeMs - stop >= Thresholds.DRIVING_BREAK_RESET_MS) { drivingStart = null; suggested = false }
            stoppedSince = null
            val start = drivingStart ?: timeMs.also { drivingStart = it }
            if (!suggested && timeMs - start >= Thresholds.CONTINUOUS_DRIVING_MS) {
                suggested = true
                return Alert(AlertType.BREAK_SUGGESTION, AlertLevel.VOICE, timeMs, "break")
            }
        } else if (stoppedSince == null) stoppedSince = timeMs
        return null
    }

    fun continuousMs(timeMs: Long): Long = drivingStart?.let { timeMs - it } ?: 0
}

/** FR-5.4: yuqori tezlikda ekranni tez-tez yoqish. */
class ScreenDistractionDetector {
    private val events = ArrayDeque<Long>()

    fun onScreenOn(timeMs: Long, speedKmh: Double): Alert? {
        if (speedKmh <= Thresholds.SCREEN_DISTRACTION_SPEED_KMH) return null
        events.addLast(timeMs)
        while (events.isNotEmpty() && timeMs - events.first() > Thresholds.SCREEN_ON_WINDOW_MS) events.removeFirst()
        if (events.size >= Thresholds.SCREEN_ON_COUNT) {
            events.clear()
            return Alert(AlertType.PHONE_DISTRACTION, AlertLevel.VOICE, timeMs, "distraction")
        }
        return null
    }
}
