package uz.hamroh.core.drive

import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.LocationFix

/**
 * FR-2.5: GPS tezligini 1 o'lchovli Kalman filtri bilan tekislash.
 * Holat: tezlik (m/s). Jarayon shovqini tezlanishga mos, o'lchov shovqini GPS aniqligiga bog'liq.
 */
class SpeedFilter(
    private val processNoise: Double = 0.6,   // (m/s²)² — mashina tezlanishi
    private val baseMeasNoise: Double = 1.0,  // (m/s)²
) {
    private var x = 0.0
    private var p = 10.0
    private var lastT: Long? = null

    val speedMps get() = x

    fun update(fix: LocationFix): Double {
        val t0 = lastT
        if (t0 == null) {
            x = fix.speedMps.coerceAtLeast(0.0); p = 1.0; lastT = fix.timeMs
            return x
        }
        val dt = ((fix.timeMs - t0) / 1000.0).coerceIn(0.0, 10.0)
        lastT = fix.timeMs
        p += processNoise * dt * dt + 0.01
        val r = baseMeasNoise * (fix.accuracyM / 5.0).coerceIn(1.0, 10.0)
        val k = p / (p + r)
        x += k * (fix.speedMps - x)
        p *= (1 - k)
        if (x < 0) x = 0.0
        return x
    }

    fun reset() { lastT = null; x = 0.0; p = 10.0 }
}

/** FR-2.6: tezlikka moslashuvchan ko'rish ufqi, metr. */
object LookAhead {
    fun horizonM(speedKmh: Double): Double = when {
        speedKmh >= Thresholds.LOOKAHEAD_HIGHWAY_FROM_KMH -> Thresholds.LOOKAHEAD_HIGHWAY_M
        speedKmh >= Thresholds.LOOKAHEAD_ARTERIAL_FROM_KMH -> Thresholds.LOOKAHEAD_ARTERIAL_M
        else -> Thresholds.LOOKAHEAD_URBAN_M
    }

    /** FR-2.2: POI ogohlantirish masofasi = max(150, v·8). */
    fun poiWarnDistanceM(speedMps: Double): Double =
        maxOf(Thresholds.POI_MIN_WARN_DISTANCE_M, speedMps * Thresholds.POI_WARN_LEAD_S)

    /** TZ 5.1: to'xtash yo'li S = (v/3.6)·t + v²/(250·μ), v — km/soat. */
    fun brakingDistanceM(speedKmh: Double, reactionS: Double = 1.0, mu: Double = 0.7): Double =
        speedKmh / 3.6 * reactionS + speedKmh * speedKmh / (250.0 * mu)
}

/** Haydash holati (FR-1.1, FR-1.2). */
enum class DriveState { IDLE, DRIVING, STANDBY, STOPPED }

/**
 * FR-1.1/1.2: GPS tezligi bo'yicha haydash boshlanishi va tugashini aniqlaydi.
 * Bluetooth yoki Activity Recognition signali [onVehicleSignal] orqali darhol DRIVING ga o'tkazadi.
 */
class DriveStateMachine {
    var state = DriveState.IDLE
        private set
    private var fastSince: Long? = null
    private var slowSince: Long? = null

    fun onVehicleSignal(timeMs: Long) {
        state = DriveState.DRIVING; fastSince = timeMs; slowSince = null
    }

    fun update(timeMs: Long, speedKmh: Double): DriveState {
        when (state) {
            DriveState.IDLE, DriveState.STOPPED -> {
                if (speedKmh > Thresholds.AUTOSTART_SPEED_KMH) {
                    val since = fastSince ?: timeMs.also { fastSince = it }
                    if (timeMs - since >= Thresholds.AUTOSTART_HOLD_MS) {
                        state = DriveState.DRIVING; slowSince = null
                    }
                } else fastSince = null
            }
            DriveState.DRIVING, DriveState.STANDBY -> {
                if (speedKmh < Thresholds.IDLE_SPEED_KMH) {
                    val since = slowSince ?: timeMs.also { slowSince = it }
                    val idle = timeMs - since
                    state = when {
                        idle >= Thresholds.IDLE_STOP_MS -> { fastSince = null; DriveState.STOPPED }
                        idle >= Thresholds.IDLE_STANDBY_MS -> DriveState.STANDBY
                        else -> state
                    }
                } else {
                    slowSince = null
                    state = DriveState.DRIVING
                }
            }
        }
        return state
    }
}
