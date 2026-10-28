package uz.hamroh.core

import org.junit.Assert.assertTrue
import org.junit.Test
import uz.hamroh.core.alerts.LocalClock
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.AlertType
import uz.hamroh.core.model.LocationFix
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType

/** Ilovadagi "Namoyish safari" (FR-11) marshruti haqiqiy dvigatelda kutilgan ogohlantirishlarni berishi kerak. */
class DemoRouteTest {
    @Test fun demoRouteRaisesAllExpectedAlerts() {
        val origin = LatLon(40.3790, 71.7860)
        fun at(m: Double) = Geo.destination(origin, 0.0, m)
        val pois = listOf(
            Poi("demo-school", PoiType.SCHOOL, at(350.0).lat, at(350.0).lon, speedLimitKmh = 30),
            Poi("demo-cross", PoiType.CROSSWALK, at(600.0).lat, at(600.0).lon),
            Poi("demo-cam", PoiType.SPEED_CAMERA, at(1300.0).lat, at(1300.0).lon, speedLimitKmh = 60, directionDeg = 0.0),
            Poi("demo-bump", PoiType.SPEED_BUMP, at(1650.0).lat, at(1650.0).lon),
            Poi("demo-hole", PoiType.POTHOLE, at(1900.0).lat, at(1900.0).lon),
        )
        val engine = RiskEngine()
        engine.onVehicleSignal(0)
        var pos = 0.0; var speed = 0.0; var t = 0
        val raised = ArrayList<AlertType>()
        while (pos < 2_150.0) {
            val target = when { t < 3 -> 0.0; pos < 250 -> 40.0; pos < 700 -> 42.0; pos < 1_550 -> 80.0; else -> 40.0 }
            speed += (target - speed).coerceIn(-12.0, 8.0)
            pos += speed / 3.6
            val p = at(pos)
            val ev = engine.onLocation(LocationFix(t * 1000L, p.lat, p.lon, speed / 3.6, if (speed > 3) 0.0 else null, 4.0), pois, LocalClock(1, 480))
            ev.filterIsInstance<EngineEvent.Raise>().forEach { raised += it.alert.type }
            t++
        }
        println("demo alerts: $raised")
        for (want in listOf(AlertType.SCHOOL_ZONE, AlertType.SPEEDING, AlertType.CROSSWALK, AlertType.SPEED_CAMERA, AlertType.SPEED_BUMP, AlertType.POTHOLE)) {
            assertTrue("$want yo'q: $raised", want in raised)
        }
    }
}
