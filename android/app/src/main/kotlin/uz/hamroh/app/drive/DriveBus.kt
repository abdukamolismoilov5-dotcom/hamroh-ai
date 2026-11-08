package uz.hamroh.app.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.hamroh.core.drive.DriveState
import uz.hamroh.core.model.Alert
import uz.hamroh.core.safety.CrashSosMachine
import uz.hamroh.core.vision.Box
import uz.hamroh.core.vision.TrackRisk

/** Xizmat holati: UI va overlay shu yerdan o'qiydi (bitta jarayon ichida). */
data class DriveUi(
    val running: Boolean = false,
    val driveState: DriveState = DriveState.IDLE,
    val speedKmh: Double = 0.0,
    val limitKmh: Int? = null,
    val inSchoolZone: Boolean = false,
    val schoolEnhanced: Boolean = false,
    val lastAlert: Alert? = null,
    val gpsOk: Boolean = false,
    val cameraStatus: String = "off",
    val visionFps: Float = 0f,
    val night: Boolean = false,
    val thermal: Int = 0,
    val dashcamRecording: Boolean = false,
    val sosState: CrashSosMachine.State = CrashSosMachine.State.MONITORING,
    val sosRemainingS: Int = 0,
    val tripDistanceKm: Double = 0.0,
    val continuousDrivingMin: Long = 0,
    val shadowMode: Boolean = true,
    val nearbyPoiCount: Int = 0,
    val poiStatus: String = "",
    val risks: List<TrackRisk> = emptyList(),
    val detectionsCount: Int = 0,
    val earValue: Float? = null,
    val faceBox: Box? = null,
    val facePoints: List<Pair<Float, Float>> = emptyList(),
    val horizonY: Float = 0.45f,
    val lat: Double? = null,
    val lon: Double? = null,
    val liveTripUrl: String? = null,
)

object DriveBus {
    private val _state = MutableStateFlow(DriveUi())
    val state: StateFlow<DriveUi> = _state.asStateFlow()

    fun update(f: (DriveUi) -> DriveUi) {
        while (true) {
            val cur = _state.value
            if (_state.compareAndSet(cur, f(cur))) return
        }
    }
}
