package uz.hamroh.core

import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon

/** Testlar uchun sintetik trek generatori (TrackReplay formatida). */
class TrackBuilder(start: LatLon, private val bearing: Double) {
    private val sb = StringBuilder()
    private var pos = start
    var t = 0L
        private set

    fun clock(day: Int, minute: Int) = apply { sb.append("CLOCK,$day,$minute\n") }
    fun poi(id: String, type: String, at: LatLon, limit: Int? = null) =
        apply { sb.append("POI,$id,$type,${at.lat},${at.lon}${limit?.let { ",$it" } ?: ""}\n") }

    /** Har soniyada GPS nuqtasi, [seconds] davomida [speedKmh] tezlikda. */
    fun drive(seconds: Int, speedKmh: Double, imuHz: Int = 0, vertical: (Long) -> Double = { 0.0 }) = apply {
        val v = speedKmh / 3.6
        repeat(seconds) {
            sb.append("L,$t,${pos.lat},${pos.lon},$v,$bearing\n")
            if (imuHz > 0) {
                val step = 1000L / imuHz
                var k = 0L
                while (k < 1000L) { sb.append("I,${t + k},1.0,${vertical(t + k)}\n"); k += step }
            }
            pos = Geo.destination(pos, bearing, v)
            t += 1000
        }
    }

    fun imu(totalG: Double, vertical: Double = 0.0, dtMs: Long = 20) = apply { sb.append("I,$t,$totalG,$vertical\n"); t += dtMs }
    fun still(seconds: Int, hz: Int = 10) = apply {
        repeat(seconds) {
            sb.append("L,$t,${pos.lat},${pos.lon},0.0,-\n")
            var k = 0L
            while (k < 1000L) { sb.append("I,${t + k},1.0,0.0\n"); k += 1000L / hz }
            t += 1000
        }
    }
    fun tick() = apply { sb.append("T,$t\n") }
    fun raw(line: String) = apply { sb.append(line).append('\n') }
    fun here() = pos
    fun build() = sb.toString()
}

val TASHKENT = LatLon(41.311, 69.279)
