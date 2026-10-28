package uz.hamroh.core

import uz.hamroh.core.alerts.LocalClock
import uz.hamroh.core.alerts.PoiAlertEvaluator
import uz.hamroh.core.alerts.SchoolSchedule
import uz.hamroh.core.alerts.ZoneStatus
import uz.hamroh.core.crowd.HardBrakeDetector
import uz.hamroh.core.crowd.PotholeDetector
import uz.hamroh.core.crowd.RoadEvent
import uz.hamroh.core.drive.DriveState
import uz.hamroh.core.drive.DriveStateMachine
import uz.hamroh.core.drive.LookAhead
import uz.hamroh.core.drive.SpeedFilter
import uz.hamroh.core.driver.DrivingTimeTracker
import uz.hamroh.core.driver.FatigueDetector
import uz.hamroh.core.driver.ScreenDistractionDetector
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.ImuSample
import uz.hamroh.core.model.LocationFix
import uz.hamroh.core.model.Poi
import uz.hamroh.core.safety.CrashSosMachine
import uz.hamroh.core.vision.CameraGeometry
import uz.hamroh.core.vision.Detection
import uz.hamroh.core.vision.Tracker
import uz.hamroh.core.vision.TrackRisk
import uz.hamroh.core.vision.VisionRiskEvaluator

/** Dvigatel chiqaradigan hodisalar. */
sealed interface EngineEvent {
    data class Raise(val alert: Alert) : EngineEvent
    /** Jamoaviy xarita uchun hodisa, koordinata bilan (FR-3.1, FR-3.2). */
    data class Road(val event: RoadEvent, val at: LatLon?) : EngineEvent
    data class Crash(val event: CrashSosMachine.Event, val at: LatLon?) : EngineEvent
    data class DriveChanged(val state: DriveState) : EngineEvent
}

/**
 * Barcha xavf qoidalari bir joyda (TZ 2-bo'lim: SignalBus → RiskEngine → AlertArbiter).
 * Android'ga bog'liq emas: barcha kirishlar oddiy ma'lumot sinflari, vaqt tashqaridan beriladi.
 * Bir oqimdan chaqirilishi kerak (Android qatlamida bitta coroutine dispatcher).
 */
class RiskEngine(schedule: SchoolSchedule = SchoolSchedule()) {
    private val speedFilter = SpeedFilter()
    private val drive = DriveStateMachine()
    private val poiEval = PoiAlertEvaluator(schedule)
    private val hardBrake = HardBrakeDetector()
    private val pothole = PotholeDetector()
    private val crash = CrashSosMachine()
    private val fatigue = FatigueDetector()
    private val drivingTime = DrivingTimeTracker()
    private val distraction = ScreenDistractionDetector()
    @Volatile private var geometry = CameraGeometry()
    private val tracker = Tracker { geometry }
    private val vision = VisionRiskEvaluator()

    var lastFix: LocationFix? = null
        private set
    val speedKmh get() = speedFilter.speedMps * 3.6
    val speedMps get() = speedFilter.speedMps
    val driveState get() = drive.state
    val zone: ZoneStatus get() = poiEval.zone
    val sosState get() = crash.state
    val visionRisks: List<TrackRisk> get() = vision.lastRisks
    /** Tungi rejim (kamera yorug'ligi yoki vaqt bo'yicha Android qatlami belgilaydi). */
    var isNight = false
    /** Foydalanuvchi kamerani qo'lda yoqqan (FR-4.12). */
    var cameraForced = false

    fun setGeometry(g: CameraGeometry) { geometry = g }
    fun geometry() = geometry

    fun onVehicleSignal(timeMs: Long): EngineEvent = EngineEvent.DriveChanged(drive.also { it.onVehicleSignal(timeMs) }.state)

    fun onLocation(fix: LocationFix, nearby: List<Poi>, clock: LocalClock): List<EngineEvent> {
        val out = ArrayList<EngineEvent>()
        lastFix = fix
        val v = speedFilter.update(fix)
        val before = drive.state
        val st = drive.update(fix.timeMs, v * 3.6)
        if (st != before) out += EngineEvent.DriveChanged(st)
        if (st == DriveState.DRIVING) {
            poiEval.evaluate(fix, v, nearby, clock).forEach { out += EngineEvent.Raise(it) }
        }
        hardBrake.onSpeed(fix.timeMs, fix.speedMps)?.let { out += EngineEvent.Road(it, fix.point) }
        drivingTime.update(fix.timeMs, v * 3.6 > Thresholds.IDLE_SPEED_KMH)?.let { out += EngineEvent.Raise(it) }
        return out
    }

    fun onImu(s: ImuSample): List<EngineEvent> {
        val out = ArrayList<EngineEvent>()
        val at = lastFix?.point
        pothole.onSample(s, speedKmh)?.let { out += EngineEvent.Road(it, at) }
        crash.onSample(s, speedKmh).forEach { out += EngineEvent.Crash(it, at) }
        return out
    }

    fun tick(nowMs: Long): List<EngineEvent> =
        crash.tick(nowMs)?.let { listOf(EngineEvent.Crash(it, lastFix?.point)) } ?: emptyList()

    fun sosRemainingMs(nowMs: Long) = crash.remainingMs(nowMs)
    fun cancelSos(nowMs: Long): EngineEvent = EngineEvent.Crash(crash.cancel(nowMs), lastFix?.point)
    fun resetSos() = crash.reset()

    /** FR-4.12: kamera tahlili hozir kerakmi. */
    fun shouldRunCamera(): Boolean {
        if (drive.state != DriveState.DRIVING) return cameraForced
        return cameraForced || zone.inSchoolZone || isNight || speedKmh > Thresholds.CAMERA_ON_SPEED_KMH
    }

    fun onDetections(timeMs: Long, detections: List<Detection>): List<EngineEvent> {
        val tracks = tracker.update(timeMs, detections)
        return vision.evaluate(
            timeMs, tracks, geometry, speedFilter.speedMps,
            LookAhead.horizonM(speedKmh), zone.inSchoolZone,
        ).map { EngineEvent.Raise(it) }
    }

    fun onEar(timeMs: Long, ear: Float?): List<EngineEvent> =
        fatigue.onEar(timeMs, ear)?.let { listOf(EngineEvent.Raise(it)) } ?: emptyList()

    fun onScreenOn(timeMs: Long): List<EngineEvent> =
        distraction.onScreenOn(timeMs, speedKmh)?.let { listOf(EngineEvent.Raise(it)) } ?: emptyList()

    fun continuousDrivingMs(nowMs: Long) = drivingTime.continuousMs(nowMs)

    fun resetVision() = tracker.clear()
}
