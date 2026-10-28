package uz.hamroh.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.hamroh.core.alerts.AlertArbiter
import uz.hamroh.core.alerts.SchoolSchedule
import uz.hamroh.core.care.CarCare
import uz.hamroh.core.care.DriverScore
import uz.hamroh.core.care.WeekStats
import uz.hamroh.core.crowd.HardBrakeDetector
import uz.hamroh.core.crowd.PotholeDetector
import uz.hamroh.core.drive.DriveState
import uz.hamroh.core.drive.DriveStateMachine
import uz.hamroh.core.drive.LookAhead
import uz.hamroh.core.drive.SpeedFilter
import uz.hamroh.core.driver.DrivingTimeTracker
import uz.hamroh.core.driver.FatigueDetector
import uz.hamroh.core.driver.ScreenDistractionDetector
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType
import uz.hamroh.core.model.ImuSample
import uz.hamroh.core.model.LocationFix
import uz.hamroh.core.safety.CrashSosMachine
import kotlin.math.abs

class GeoTest {
    @Test fun distanceTashkentSamarkand() {
        val d = Geo.distanceM(LatLon(41.2995, 69.2401), LatLon(39.6542, 66.9597))
        assertTrue("got $d", abs(d - 265_000) < 10_000)
    }
    @Test fun destinationRoundTrip() {
        val b = Geo.destination(TASHKENT, 73.0, 500.0)
        assertEquals(500.0, Geo.distanceM(TASHKENT, b), 0.5)
        assertEquals(73.0, Geo.bearingDeg(TASHKENT, b), 0.1)
    }
    @Test fun angleDiff() {
        assertEquals(20.0, Geo.angleDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geo.angleDiffDeg(0.0, 180.0), 1e-9)
    }
    @Test fun brakingDistanceFormula() {
        // 60 km/soat: 16.67 + 3600/175 = 37.2 m
        assertEquals(37.24, LookAhead.brakingDistanceM(60.0), 0.05)
    }
    @Test fun lookahead() {
        assertEquals(45.0, LookAhead.horizonM(40.0), 0.0)
        assertEquals(80.0, LookAhead.horizonM(70.0), 0.0)
        assertEquals(140.0, LookAhead.horizonM(100.0), 0.0)
        assertEquals(150.0, LookAhead.poiWarnDistanceM(10.0), 0.0)
        assertEquals(222.2, LookAhead.poiWarnDistanceM(100 / 3.6), 0.1)
    }
}

class DriveTest {
    @Test fun speedFilterSmoothsNoise() {
        val f = SpeedFilter()
        var last = 0.0
        for (i in 0 until 30) last = f.update(LocationFix(i * 1000L, 0.0, 0.0, 15.0 + if (i % 2 == 0) 2.0 else -2.0, 0.0))
        assertTrue("got $last", abs(last - 15.0) < 1.0)
    }
    @Test fun speedFilterFollowsStep() {
        val f = SpeedFilter()
        f.update(LocationFix(0, 0.0, 0.0, 0.0, 0.0))
        var last = 0.0
        for (i in 1..6) last = f.update(LocationFix(i * 1000L, 0.0, 0.0, 20.0, 0.0))
        assertTrue("got $last", last > 18.0)
    }
    @Test fun autostartAfter60s() {
        val m = DriveStateMachine()
        for (s in 0..59) assertEquals(DriveState.IDLE, m.update(s * 1000L, 30.0))
        assertEquals(DriveState.DRIVING, m.update(60_000, 30.0))
    }
    @Test fun dipBelowThresholdResetsAutostart() {
        val m = DriveStateMachine()
        for (s in 0..40) m.update(s * 1000L, 30.0)
        m.update(41_000, 5.0)
        for (s in 42..90) m.update(s * 1000L, 30.0)
        assertEquals(DriveState.IDLE, m.update(91_000, 30.0))
        assertEquals(DriveState.DRIVING, m.update(102_000, 30.0))
    }
    @Test fun standbyThenStop() {
        val m = DriveStateMachine()
        m.onVehicleSignal(0)
        m.update(1_000, 0.0)
        assertEquals(DriveState.DRIVING, m.update(4 * 60_000L, 0.0))
        assertEquals(DriveState.STANDBY, m.update(5 * 60_000L + 1_000, 0.0))
        assertEquals(DriveState.STOPPED, m.update(15 * 60_000L + 1_000, 0.0))
    }
}

class ArbiterTest {
    private fun a(type: AlertType, t: Long, key: String = type.name) = Alert(type, AlertLevel.VOICE, t, key)

    @Test fun dedupWithin30s() {
        val arb = AlertArbiter()
        assertTrue(arb.submit(a(AlertType.SPEED_CAMERA, 0)) is AlertArbiter.Decision.Play)
        arb.onFinished(2_000)
        assertTrue(arb.submit(a(AlertType.SPEED_CAMERA, 10_000)) is AlertArbiter.Decision.Suppressed)
        assertTrue(arb.submit(a(AlertType.SPEED_CAMERA, 31_000)) is AlertArbiter.Decision.Play)
    }
    @Test fun higherPriorityInterrupts() {
        val arb = AlertArbiter()
        arb.submit(a(AlertType.SPEED_CAMERA, 0))
        val d = arb.submit(a(AlertType.PEDESTRIAN, 100))
        assertTrue(d is AlertArbiter.Decision.Interrupt)
        assertEquals(AlertType.PEDESTRIAN, arb.current?.type)
    }
    @Test fun lowerPriorityQueuedThenPlayed() {
        val arb = AlertArbiter()
        arb.submit(a(AlertType.SCHOOL_ZONE, 0))
        assertTrue(arb.submit(a(AlertType.SPEED_BUMP, 100)) is AlertArbiter.Decision.Queued)
        assertTrue(arb.submit(a(AlertType.SPEED_CAMERA, 150)) is AlertArbiter.Decision.Queued)
        assertEquals(AlertType.SPEED_CAMERA, arb.onFinished(1_500)?.type)
        assertEquals(AlertType.SPEED_BUMP, arb.onFinished(3_000)?.type)
    }
    @Test fun staleQueuedDropped() {
        val arb = AlertArbiter()
        arb.submit(a(AlertType.SCHOOL_ZONE, 0))
        arb.submit(a(AlertType.SPEED_BUMP, 100))
        assertNull(arb.onFinished(10_000))
    }
    @Test fun shadowModeSilences() {
        val arb = AlertArbiter()
        arb.shadowTypes = setOf(AlertType.PEDESTRIAN)
        assertTrue(arb.submit(a(AlertType.PEDESTRIAN, 0)) is AlertArbiter.Decision.Shadowed)
        assertTrue(arb.submit(a(AlertType.PEDESTRIAN, 500)) is AlertArbiter.Decision.Suppressed) // dedup
        assertEquals(1, arb.shadowLog.size)
        assertNull(arb.current)
    }
}

class RoadEventsTest {
    @Test fun potholeDetected() {
        val d = PotholeDetector()
        val samples = listOf(0.0, 0.5, -4.0, 5.0, 0.2)
        val ev = samples.mapIndexedNotNull { i, v -> d.onSample(ImuSample(i * 50L, 1.0, v), 40.0) }
        assertEquals(1, ev.size)
        assertEquals(9.0, ev[0].severity, 1e-9)
    }
    @Test fun potholeIgnoredAtLowSpeed() {
        val d = PotholeDetector()
        val ev = listOf(-4.0, 5.0).mapIndexedNotNull { i, v -> d.onSample(ImuSample(i * 50L, 1.0, v), 10.0) }
        assertTrue(ev.isEmpty())
    }
    @Test fun singleBumpNotPothole() {
        val d = PotholeDetector()
        val ev = listOf(0.0, 4.5, 4.0, 0.5).mapIndexedNotNull { i, v -> d.onSample(ImuSample(i * 50L, 1.0, v), 40.0) }
        assertTrue(ev.isEmpty())
    }
    @Test fun hardBrakeFromGps() {
        val d = HardBrakeDetector()
        assertNull(d.onSpeed(0, 16.0))
        assertNull(d.onSpeed(1000, 15.0)) // 1 m/s²
        val e = d.onSpeed(2000, 10.0)     // 5 m/s²
        assertNotNull(e)
        assertNull(d.onSpeed(3000, 5.0))  // xuddi shu tormoz — takrorlanmaydi
    }
}

class CrashSosTest {
    private fun feed(m: CrashSosMachine, t0: Long, ms: Long, g: Double, speed: Double, step: Long = 100): List<CrashSosMachine.Event> {
        val out = ArrayList<CrashSosMachine.Event>()
        var t = t0
        while (t < t0 + ms) { out += m.onSample(ImuSample(t, g, 0.0), speed); t += step }
        return out
    }

    @Test fun impactStillnessCountdownTrigger() {
        val m = CrashSosMachine()
        val e1 = m.onSample(ImuSample(0, 6.0, 0.0), 50.0)
        assertTrue(e1.any { it is CrashSosMachine.Event.ProtectClip })
        assertEquals(CrashSosMachine.State.IMPACT_WAIT, m.state)
        val e2 = feed(m, 100, 10_100, 1.0, 0.0)
        assertTrue(e2.any { it is CrashSosMachine.Event.CountdownStarted })
        assertEquals(CrashSosMachine.State.COUNTDOWN, m.state)
        val e3 = feed(m, 10_200, 30_100, 1.0, 0.0)
        assertTrue(e3.any { it is CrashSosMachine.Event.SosTriggered })
    }
    @Test fun cancelStopsSos() {
        val m = CrashSosMachine()
        m.onSample(ImuSample(0, 6.0, 0.0), 50.0)
        feed(m, 100, 10_100, 1.0, 0.0)
        m.cancel(11_000)
        val e = feed(m, 11_000, 40_000, 1.0, 0.0)
        assertFalse(e.any { it is CrashSosMachine.Event.SosTriggered })
    }
    @Test fun phoneDropIsNotSos() {
        val m = CrashSosMachine()
        feed(m, 0, 300, 0.05, 0.0, step = 20)        // erkin tushish 300 ms
        val e = m.onSample(ImuSample(320, 8.0, 0.0), 0.0)
        assertTrue(e.any { it is CrashSosMachine.Event.PhoneDropped })
        assertEquals(CrashSosMachine.State.MONITORING, m.state)
    }
    @Test fun bumpThenContinueDrivingIsNotSos() {
        val m = CrashSosMachine()
        m.onSample(ImuSample(0, 4.5, 0.0), 40.0)
        val e = feed(m, 100, 70_000, 1.0, 40.0, step = 500)
        assertFalse(e.any { it is CrashSosMachine.Event.CountdownStarted })
        assertEquals(CrashSosMachine.State.MONITORING, m.state)
    }
    @Test fun tickTriggersWithoutImu() {
        val m = CrashSosMachine()
        m.onSample(ImuSample(0, 6.0, 0.0), 50.0)
        feed(m, 100, 10_100, 1.0, 0.0)
        assertNull(m.tick(20_000))
        assertTrue(m.tick(41_000) is CrashSosMachine.Event.SosTriggered)
    }
}

class DriverStateTest {
    @Test fun eyesClosed1500ms() {
        val f = FatigueDetector()
        var alert: Alert? = null
        for (t in 0..1600 step 100) alert = f.onEar(t.toLong(), 0.1f) ?: alert
        assertNotNull(alert)
        assertEquals("eyes_closed", alert!!.args["reason"])
    }
    @Test fun normalBlinksNoAlert() {
        val f = FatigueDetector()
        var n = 0
        var t = 0L
        repeat(30) {
            repeat(2) { if (f.onEar(t, 0.1f) != null) n++; t += 100 }   // 200 ms miltillash
            repeat(20) { if (f.onEar(t, 0.3f) != null) n++; t += 100 }
        }
        assertEquals(0, n)
    }
    @Test fun threeLongBlinksInMinute() {
        val f = FatigueDetector()
        var alert: Alert? = null
        var t = 0L
        repeat(3) {
            repeat(7) { alert = f.onEar(t, 0.1f) ?: alert; t += 100 }   // 700 ms
            repeat(100) { alert = f.onEar(t, 0.3f) ?: alert; t += 100 }
        }
        assertEquals("long_blinks", alert?.args?.get("reason"))
    }
    @Test fun breakAfterTwoHours() {
        val d = DrivingTimeTracker()
        assertNull(d.update(0, true))
        assertNull(d.update(119 * 60_000L, true))
        assertNotNull(d.update(120 * 60_000L, true))
        assertNull(d.update(121 * 60_000L, true))
    }
    @Test fun longStopResetsDriving() {
        val d = DrivingTimeTracker()
        d.update(0, true)
        d.update(100 * 60_000L, false)
        d.update(116 * 60_000L, true)
        assertNull(d.update(200 * 60_000L, true))
        assertNotNull(d.update(236 * 60_000L, true))
    }
    @Test fun screenDistraction() {
        val s = ScreenDistractionDetector()
        assertNull(s.onScreenOn(0, 80.0))
        assertNull(s.onScreenOn(60_000, 80.0))
        assertNotNull(s.onScreenOn(120_000, 80.0))
        assertNull(s.onScreenOn(130_000, 40.0))
    }
}

class CarCareTest {
    @Test fun oil() {
        assertEquals(400.0, CarCare.oilKmLeft(56_600.0, 50_000.0), 1e-9)
        assertTrue(CarCare.oilReminderDue(400.0, null))
        assertFalse(CarCare.oilReminderDue(400.0, 450.0))
        assertTrue(CarCare.oilReminderDue(-10.0, 450.0))
        assertFalse(CarCare.oilReminderDue(900.0, null))
    }
    @Test fun docStages() {
        assertNull(CarCare.docStageDue(40, null))
        assertEquals(30, CarCare.docStageDue(25, null))
        assertNull(CarCare.docStageDue(20, 30))
        assertEquals(7, CarCare.docStageDue(6, 30))
        assertEquals(1, CarCare.docStageDue(0, 7))
        assertEquals(1, CarCare.docStageDue(-3, 7))
        assertNull(CarCare.docStageDue(-3, 1))
    }
    @Test fun score() {
        assertNull(DriverScore.compute(WeekStats(5.0, 0, 0, 0.0, 0, 0)))
        assertEquals(100, DriverScore.compute(WeekStats(200.0, 0, 0, 0.0, 0, 0)))
        assertEquals(89, DriverScore.compute(WeekStats(200.0, 4, 2, 0.0, 0, 0)))
        assertEquals(0, DriverScore.compute(WeekStats(20.0, 30, 30, 20.0, 5, 5)))
    }
    @Test fun schoolSchedule() {
        val s = SchoolSchedule()
        assertTrue(s.isActive(1, 8 * 60))
        assertFalse(s.isActive(1, 10 * 60))
        assertFalse(s.isActive(7, 8 * 60))
    }
}
