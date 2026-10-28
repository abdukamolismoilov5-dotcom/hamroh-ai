package uz.hamroh.core.alerts

import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.Alert

/**
 * FR-10.2: ogohlantirishlar navbati.
 * - Bir xil [Alert.dedupKey] [Thresholds.ALERT_DEDUP_MS] ichida qayta aytilmaydi.
 * - Yuqori ustuvorlikli xabar pastrog'ini to'xtatadi ([Decision.Interrupt]).
 * - Bir vaqtda bitta xabar o'ynaladi; qolganlari ustuvorlik bo'yicha kutadi, eskirganlari tashlanadi.
 */
class AlertArbiter(
    private val maxQueueAgeMs: Long = 12_000L,
) {
    sealed interface Decision {
        data class Play(val alert: Alert) : Decision
        data class Interrupt(val alert: Alert, val stopped: Alert) : Decision
        data object Queued : Decision
        data object Suppressed : Decision
        /** Kuzatuv rejimi: ovoz chiqmaydi, lekin jurnalga yozilishi kerak (dedup'dan o'tgan). */
        data object Shadowed : Decision
    }

    private val lastSpoken = HashMap<String, Long>()
    private val queue = ArrayList<Alert>()
    var current: Alert? = null
        private set
    /** Kuzatuv rejimi (FR-4.9): bu turlar ovoz chiqarmaydi, faqat [shadowLog] ga yoziladi. */
    var shadowTypes: Set<uz.hamroh.core.model.AlertType> = emptySet()
    val shadowLog = ArrayList<Alert>()

    fun submit(alert: Alert): Decision {
        // Agar oldingi xabar 7 soniyadan ortiq osilib qolgan bo'lsa, avtomatik tozalaymiz
        val existing = current
        if (existing != null && alert.timeMs - existing.timeMs > 7_000L) {
            current = null
        }

        val last = lastSpoken[alert.dedupKey]
        if (last != null && alert.timeMs - last < Thresholds.ALERT_DEDUP_MS) return Decision.Suppressed
        if (alert.type in shadowTypes) {
            lastSpoken[alert.dedupKey] = alert.timeMs
            shadowLog += alert
            if (shadowLog.size > 500) shadowLog.removeAt(0)
            return Decision.Shadowed
        }
        if (queue.any { it.dedupKey == alert.dedupKey || it.type == alert.type } || current?.dedupKey == alert.dedupKey) {
            return Decision.Suppressed
        }
        val cur = current
        return when {
            cur == null -> { start(alert); Decision.Play(alert) }
            // Faqat SOS halokat xabari joriy gapni bo'lishi mumkin; qolgan barcha o'zbekcha xabarlar to'liq oxirigacha gapiriladi!
            alert.type == uz.hamroh.core.model.AlertType.SOS_COUNTDOWN && cur.type != uz.hamroh.core.model.AlertType.SOS_COUNTDOWN -> {
                start(alert); Decision.Interrupt(alert, cur)
            }
            else -> { queue += alert; Decision.Queued }
        }
    }

    /** Boshlang'ich salomlashuv ("drive_started") gapirilayotganda boshqa xabarlar uni yarim yo'lda uzib qo'ymasligi uchun. */
    fun markBusy(nowMs: Long) {
        current = Alert(
            uz.hamroh.core.model.AlertType.BREAK_SUGGESTION,
            uz.hamroh.core.model.AlertLevel.VOICE,
            nowMs,
            "drive_started",
        )
    }

    /** O'ynash tugaganda chaqiriladi; keyingi xabarni qaytaradi (bo'lsa). */
    fun onFinished(nowMs: Long): Alert? {
        current = null
        queue.removeAll { nowMs - it.timeMs > maxQueueAgeMs }
        val next = queue.minWithOrNull(compareBy<Alert> { it.type.priority }.thenBy { it.timeMs }) ?: return null
        queue.remove(next)
        start(next.copy(timeMs = nowMs))
        return current
    }

    private fun start(alert: Alert) {
        current = alert
        lastSpoken[alert.dedupKey] = alert.timeMs
    }

    fun pending(): List<Alert> = queue.toList()

    fun clear() { queue.clear(); current = null }
}
