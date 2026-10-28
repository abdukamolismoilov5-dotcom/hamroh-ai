package uz.hamroh.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geografik nuqta (WGS-84, gradus). */
data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun toRad(deg: Double) = deg * PI / 180.0
    fun toDeg(rad: Double) = rad * 180.0 / PI

    /** Haversine masofasi, metr. */
    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = toRad(b.lat - a.lat)
        val dLon = toRad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(toRad(a.lat)) * cos(toRad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** a nuqtadan b nuqtaga boshlang'ich yo'nalish, 0..360 (0 = shimol). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val la1 = toRad(a.lat)
        val la2 = toRad(b.lat)
        val dLon = toRad(b.lon - a.lon)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return (toDeg(atan2(y, x)) + 360.0) % 360.0
    }

    /** Ikki yo'nalish orasidagi eng kichik burchak, 0..180. */
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)
        return d
    }

    /** Nuqtadan berilgan yo'nalish va masofada joylashgan nuqta. */
    fun destination(from: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        val delta = distanceM / EARTH_RADIUS_M
        val theta = toRad(bearingDeg)
        val la1 = toRad(from.lat)
        val lo1 = toRad(from.lon)
        val la2 = asin(sin(la1) * cos(delta) + cos(la1) * sin(delta) * cos(theta))
        val lo2 = lo1 + atan2(sin(theta) * sin(delta) * cos(la1), cos(delta) - sin(la1) * sin(la2))
        return LatLon(toDeg(la2), (toDeg(lo2) + 540.0) % 360.0 - 180.0)
    }

    /** Nuqta atrofidagi to'rtburchak (bbox): [janub, g'arb, shimol, sharq]. */
    fun bbox(center: LatLon, radiusM: Double): DoubleArray {
        val dLat = toDeg(radiusM / EARTH_RADIUS_M)
        val dLon = toDeg(radiusM / (EARTH_RADIUS_M * cos(toRad(center.lat)).coerceAtLeast(1e-6)))
        return doubleArrayOf(center.lat - dLat, center.lon - dLon, center.lat + dLat, center.lon + dLon)
    }

    fun kmhToMs(kmh: Double) = kmh / 3.6
    fun msToKmh(ms: Double) = ms * 3.6
}
