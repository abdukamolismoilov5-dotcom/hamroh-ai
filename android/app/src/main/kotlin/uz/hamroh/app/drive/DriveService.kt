package uz.hamroh.app.drive

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import androidx.camera.core.Preview
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.R
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.ui.MainActivity
import uz.hamroh.app.ui.SosActivity
import uz.hamroh.app.util.AlertText
import uz.hamroh.app.util.T
import uz.hamroh.app.vision.CameraHub
import uz.hamroh.app.vision.Dashcam
import uz.hamroh.core.EngineEvent
import uz.hamroh.core.RiskEngine
import uz.hamroh.core.Thresholds
import uz.hamroh.core.alerts.AlertArbiter
import uz.hamroh.core.alerts.LocalClock
import uz.hamroh.core.alerts.MinuteRange
import uz.hamroh.core.alerts.SchoolSchedule
import uz.hamroh.core.crowd.RoadEventType
import uz.hamroh.core.drive.DriveState
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType
import uz.hamroh.core.model.LocationFix
import uz.hamroh.core.model.Poi
import uz.hamroh.core.safety.CrashSosMachine
import uz.hamroh.core.vision.CameraGeometry
import uz.hamroh.core.vision.Detection
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.Executors

/**
 * Asosiy haydash xizmati (Foreground Service). TZ 2-bo'lim oqimi:
 * sensorlar (GPS, IMU, kamera) → [RiskEngine] (bitta oqimda) → [AlertArbiter] → ovoz, ekran, overlay.
 */
class DriveService : LifecycleService(), CameraHub.Listener {

    private val app get() = application as HamrohApp
    private val engineExecutor = Executors.newSingleThreadExecutor { Thread(it, "engine") }
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()
    private lateinit var engine: RiskEngine
    private val arbiter = AlertArbiter()
    private lateinit var voice: VoicePlayer
    private lateinit var sensors: SensorHub
    private lateinit var overlay: Overlay
    private var camera: CameraHub? = null
    private var dashcam: Dashcam? = null
    private var cameraFgsAllowed = false

    private var nearby: List<Poi> = emptyList()
    private var nearbyCenter: LatLon? = null
    private var nearbyAt = 0L
    private var tripId: Long? = null
    private var tripDistanceM = 0.0
    private var tripNightM = 0.0
    private var tripMaxKmh = 0.0
    private var lastPoint: LatLon? = null
    private var lastAlertText: String? = null
    private var lastAlertDbId: Long? = null
    private var thermalLevel = 0
    private var trackWriter: java.io.BufferedWriter? = null
    private var liveTrip: ServerApi.LiveTrip? = null
    private var liveTripSentAt = 0L
    private var arrivedSent = false
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pitchRad = 0.0

    /** FR-11: namoyish safari — GPS o'rniga sintetik marshrut, haqiqiy dvigatel va ovoz bilan. */
    private var demo = false
    private var demoJob: Job? = null
    private var demoPois: List<Poi> = emptyList()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation ?: return
            val fix = LocationFix(
                SystemClock.elapsedRealtime(), l.latitude, l.longitude,
                if (l.hasSpeed()) l.speed.toDouble() else 0.0,
                if (l.hasBearing() && l.speed > 1.0f) l.bearing.toDouble() else null,
                if (l.hasAccuracy()) l.accuracy.toDouble() else 10.0,
            )
            lifecycleScope.launch(engineDispatcher) { onFix(fix) }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_ON) {
                val t = SystemClock.elapsedRealtime()
                lifecycleScope.launch(engineDispatcher) { handle(engine.onScreenOn(t)) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        engine = RiskEngine(parseSchedule(app.prefs.schoolScheduleCsv))
        voice = VoicePlayer(this)
        overlay = Overlay(this)
        sensors = SensorHub(this,
            onImu = { s -> lifecycleScope.launch(engineDispatcher) { handle(engine.onImu(s)) } },
            onGravity = { gx, gy, gz -> pitchRad = CameraGeometry.pitchFromGravity(gx, gy, gz); updateGeometry() },
        )
        updateShadowMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopDriving(); return START_NOT_STICKY }
            ACTION_SOS_CANCEL -> { lifecycleScope.launch(engineDispatcher) { handle(listOf(engine.cancelSos(SystemClock.elapsedRealtime()))) }; return START_STICKY }
            ACTION_FALSE_ALERT -> { markFalseAlert(); return START_STICKY }
            ACTION_PROTECT -> { dashcam?.protect("manual"); toastAlert(T("Klip saqlandi", "Клип сохранён", "Clip saved")); return START_STICKY }
            ACTION_CAR_DISCONNECTED -> { return START_STICKY }
            ACTION_LIVE_TRIP -> { startLiveTrip(); return START_STICKY }
            ACTION_FORCE_CAMERA -> { engine.cameraForced = !engine.cameraForced; refreshCamera(); return START_STICKY }
            ACTION_REFRESH_SETTINGS -> { updateShadowMode(); refreshCamera(); return START_STICKY }
            ACTION_DEMO -> {
                if (DriveBus.state.value.running && !demo) { toastAlert(T("Avval haydashni to'xtating", "Сначала остановите поездку", "Stop the current drive first")); return START_STICKY }
                demo = true
                if (!DriveBus.state.value.running) beginDriving("demo")
                if (DriveBus.state.value.running) startDemo()
                return START_STICKY
            }
        }
        if (!DriveBus.state.value.running) {
            beginDriving(intent?.getStringExtra(EXTRA_REASON) ?: "manual")
        } else if (intent?.getStringExtra(EXTRA_REASON) == "camera") {
            engine.cameraForced = true
            refreshCamera()
        }
        return START_STICKY
    }

    // ---------------- boshlash / to'xtatish ----------------

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun beginDriving(reason: String) {
        val hasLocPerm = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) || hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
        cameraFgsAllowed = hasPerm(Manifest.permission.CAMERA) && !app.prefs.liteMode
        val notif = buildNotification(T("Hamroh yoqilgan", "Hamroh включён", "Hamroh is on"), "")
        val locType = if (hasLocPerm) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        val camType = if (cameraFgsAllowed) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        val type = (locType or camType).let { if (it == 0) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else it }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
        } catch (e: Exception) {
            // Android 14+: fon holatidan kamera turi bilan boshlash taqiqlangan — faqat joylashuv bilan
            Log.w(TAG, "kamera FGS rad etildi, faqat GPS", e)
            cameraFgsAllowed = false
            try {
                if (locType != 0) {
                    ServiceCompat.startForeground(this, NOTIF_ID, notif, locType)
                } else {
                    startForeground(NOTIF_ID, notif)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "FGS ishga tushmadi", e2); stopSelf(); return
            }
        }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hamroh:drive").apply { acquire() }
        DriveBus.update { DriveUi(running = true, shadowMode = arbiter.shadowTypes.isNotEmpty()) }
        NotificationManagerCompat.from(this).cancel(uz.hamroh.app.auto.AutoStart.NOTIF_ID)

        if (reason == "bluetooth" || reason == "activity" || reason == "manual" || reason == "notification" || reason == "demo" || reason == "camera") {
            engine.onVehicleSignal(SystemClock.elapsedRealtime())
        }
        if (reason == "manual" || reason == "demo" || reason == "camera") {
            engine.cameraForced = true
        }
        if (!demo && hasLocPerm) LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000).setMinUpdateIntervalMillis(500).build(),
            locationCallback, Looper.getMainLooper(),
        )
        sensors.start()
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        registerThermal()
        if (app.prefs.overlayEnabled) overlay.show()
        lifecycleScope.launch(Dispatchers.IO) { app.poiRepo.loadSeedIfEmpty() }
        tripId = app.db.startTrip(System.currentTimeMillis())
        tripDistanceM = 0.0; tripNightM = 0.0; tripMaxKmh = 0.0; lastPoint = null; arrivedSent = false
        if (app.prefs.recordTracks) openTrackWriter()
        setupCamera()
        tickJob = lifecycleScope.launch(engineDispatcher) {
            while (isActive) { tick(); delay(1_000) }
        }
        lifecycleScope.launch(engineDispatcher) {
            arbiter.markBusy(SystemClock.elapsedRealtime())
            voice.play("drive_started", AlertLevel.VOICE) {
                lifecycleScope.launch(engineDispatcher) {
                    arbiter.onFinished(SystemClock.elapsedRealtime())?.let { next -> speak(next) }
                }
            }
        }
    }

    private fun stopDriving() {
        tickJob?.cancel()
        demoJob?.cancel(); demoJob = null; demo = false
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(locationCallback)
        runCatching { sensors.stop() }
        runCatching { unregisterReceiver(screenReceiver) }
        unregisterThermal()
        overlay.hide()
        camera?.release(); camera = null; dashcam = null
        tripId?.let { id -> app.db.updateTrip(id, tripDistanceM, tripMaxKmh, tripNightM, System.currentTimeMillis()) }
        tripId = null
        app.prefs.odometerKm = app.prefs.odometerKm + (tripDistanceM / 1000).toFloat()
        trackWriter?.let { runCatching { it.close() } }; trackWriter = null
        liveTrip?.let { t -> val p = lastPoint; lifecycleScope.launch(Dispatchers.IO) { runCatching { app.api.updateLiveTrip(t, p?.lat ?: 0.0, p?.lon ?: 0.0, 0.0, true) } } }
        liveTrip = null
        uz.hamroh.app.data.SyncWorker.now(this)
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        DriveBus.update { DriveUi(running = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (DriveBus.state.value.running) stopDriving()
        voice.release()
        engineExecutor.shutdown()
        instance = null
        super.onDestroy()
    }

    // ---------------- kamera ----------------

    private fun setupCamera() {
        if (!cameraFgsAllowed && hasPerm(Manifest.permission.CAMERA) && !app.prefs.liteMode) {
            cameraFgsAllowed = true
        }
        if (!cameraFgsAllowed || app.prefs.cameraMode == "off") {
            DriveBus.update { it.copy(cameraStatus = if (app.prefs.liteMode) "lite" else "off") }
            return
        }
        val dc = Dashcam(this,
            onClip = { f, sha, protected, reason -> if (protected) app.db.addClip(f.absolutePath, sha, System.currentTimeMillis(), reason, true) },
            onState = { rec -> DriveBus.update { it.copy(dashcamRecording = rec) } },
        )
        dashcam = dc
        camera = CameraHub(this, this, dc, this).also { it.dashcamWanted = app.prefs.dashcamEnabled }
        refreshCamera()
    }

    /** FR-4.12 + issiqlik: kamera kerakmi va qaysi rejimda. */
    private fun refreshCamera() {
        if (!cameraFgsAllowed && hasPerm(Manifest.permission.CAMERA) && !app.prefs.liteMode) {
            cameraFgsAllowed = true
        }
        if (camera == null && cameraFgsAllowed && app.prefs.cameraMode != "off") {
            setupCamera()
            return
        }
        val hub = camera ?: return
        val mode = app.prefs.cameraMode
        val severe = thermalLevel >= 3
        val wantDashcam = app.prefs.dashcamEnabled && mode == "road"
        val active = mode != "off" && !severe && (hub.isPreviewAttached || engine.shouldRunCamera() || wantDashcam || engine.cameraForced)
        hub.analysisEnabled = mode != "off" && !severe && (hub.isPreviewAttached || engine.shouldRunCamera() || engine.cameraForced)
        hub.intervalMs = when { thermalLevel >= 2 -> 180L; else -> 65L }
        hub.dashcamWanted = app.prefs.dashcamEnabled && thermalLevel < 3
        hub.configure(mode, active)
    }

    fun attachPreview(surface: Preview.SurfaceProvider?) {
        if (surface != null) {
            engine.cameraForced = true
            if (camera == null) refreshCamera()
        }
        camera?.setPreview(surface)
        if (surface != null) refreshCamera()
    }

    override fun onRoadDetections(timeMs: Long, detections: List<Detection>, meanLuma: Int, fps: Float) {
        lifecycleScope.launch(engineDispatcher) {
            engine.isNight = meanLuma < Thresholds.NIGHT_MEAN_LUMA
            handle(engine.onDetections(timeMs, detections))
            trackWriter?.let { w ->
                val d = detections.joinToString(";") { "${it.cls},${"%.2f".format(java.util.Locale.US, it.confidence)},${it.box.left},${it.box.top},${it.box.right},${it.box.bottom}" }
                runCatching { w.write(if (d.isEmpty()) "D,$timeMs\n" else "D,$timeMs,$d\n") }
            }
            DriveBus.update {
                it.copy(
                    risks = engine.visionRisks,
                    detectionsCount = detections.size,
                    visionFps = fps,
                    night = engine.isNight,
                    horizonY = engine.geometry().horizonY.toFloat(),
                )
            }
        }
    }

    override fun onEar(timeMs: Long, ear: Float?) {
        DriveBus.update { it.copy(earValue = ear) }
        lifecycleScope.launch(engineDispatcher) { handle(engine.onEar(timeMs, ear)) }
    }

    override fun onCameraStatus(status: String) { DriveBus.update { it.copy(cameraStatus = status) } }

    private fun updateGeometry() {
        val g = CameraGeometry(cameraHeightM = app.prefs.cameraHeightM.toDouble(), pitchDownRad = pitchRad.coerceIn(-0.35, 0.35))
        lifecycleScope.launch(engineDispatcher) { engine.setGeometry(g) }
    }

    // ---------------- dvigatel ----------------

    private fun onFix(fix: LocationFix) {
        val here = fix.point
        val now = fix.timeMs
        val center = nearbyCenter
        if (demo) {
            nearby = demoPois
        } else if (center == null || now - nearbyAt > 5_000 || Geo.distanceM(center, here) > 200) {
            nearby = app.poiRepo.nearby(here, 1_500.0); nearbyCenter = here; nearbyAt = now
        }
        if (!demo && app.poiRepo.needsRefresh(here, System.currentTimeMillis())) {
            app.prefs.poiFetchedAt = System.currentTimeMillis() - 7L * 24 * 3600 * 1000 + 10 * 60_000 // 10 daqiqadan keyin qayta urinish
            lifecycleScope.launch(Dispatchers.IO) {
                val n = app.poiRepo.refreshFromOsm(here)
                DriveBus.update { it.copy(poiStatus = if (n >= 0) "OSM: $n" else T("POI yuklanmadi", "POI не загружены", "POI fetch failed")) }
                nearbyAt = 0
            }
        }
        val dt = LocalDateTime.now()
        // Namoyishda vaqt doim "dushanba 08:00" — maktab hududi kuchaytirilgan rejimini ko'rsatish uchun
        val clock = if (demo) LocalClock(1, 8 * 60) else LocalClock(dt.dayOfWeek.value, dt.hour * 60 + dt.minute)
        handle(engine.onLocation(fix, nearby, clock))
        // safar statistikasi
        lastPoint?.let { p ->
            val d = Geo.distanceM(p, here)
            if (d in 0.5..200.0 && fix.speedKmh > 3) {
                tripDistanceM += d
                if (dt.hour >= 21 || dt.hour < 6) tripNightM += d
            }
        }
        lastPoint = here
        if (fix.speedKmh > tripMaxKmh) tripMaxKmh = fix.speedKmh
        trackWriter?.let { runCatching { it.write("L,${fix.timeMs},${fix.lat},${fix.lon},${fix.speedMps},${fix.bearingDeg ?: "-"}\n") } }
        // FR-8.2: "yetib bordim"
        val hl = app.prefs.homeLat; val hn = app.prefs.homeLon
        if (app.prefs.arrivedNotify && !arrivedSent && hl != null && hn != null && tripDistanceM > 1_000 &&
            Geo.distanceM(here, LatLon(hl, hn)) < 100
        ) {
            arrivedSent = true
            lifecycleScope.launch(Dispatchers.IO) { runCatching { app.api.arrived(T("Uy", "Дом", "Home"), here.lat, here.lon) } }
        }
        DriveBus.update {
            it.copy(
                speedKmh = engine.speedKmh, limitKmh = engine.zone.speedLimitKmh, inSchoolZone = engine.zone.inSchoolZone,
                schoolEnhanced = engine.zone.enhanced, gpsOk = fix.accuracyM < 30, driveState = engine.driveState,
                tripDistanceKm = tripDistanceM / 1000, nearbyPoiCount = nearby.size, lat = fix.lat, lon = fix.lon,
            )
        }
    }

    private suspend fun tick() {
        val now = SystemClock.elapsedRealtime()
        handle(engine.tick(now))
        refreshCamera()
        dashcam?.appendMeta(lastPoint?.lat, lastPoint?.lon, engine.speedKmh)
        val ui = DriveBus.state.value
        DriveBus.update {
            it.copy(
                sosState = engine.sosState, sosRemainingS = (engine.sosRemainingMs(now) / 1000).toInt(),
                continuousDrivingMin = engine.continuousDrivingMs(now) / 60_000, thermal = thermalLevel,
            )
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) { overlay.render(DriveBus.state.value, lastAlertText) }
        tripId?.let { if (now / 1000 % 30 == 0L) app.db.updateTrip(it, tripDistanceM, tripMaxKmh, tripNightM, null) }
        liveTrip?.let { t ->
            if (now - liveTripSentAt > 30_000) {
                liveTripSentAt = now
                val p = lastPoint ?: return@let
                lifecycleScope.launch(Dispatchers.IO) { runCatching { app.api.updateLiveTrip(t, p.lat, p.lon, ui.speedKmh, false) } }
            }
        }
        updateNotification()
    }

    /** Dvigatel hodisalarini qayta ishlash (engine oqimida). */
    private fun handle(events: List<EngineEvent>) {
        for (e in events) when (e) {
            is EngineEvent.Raise -> raise(e.alert)
            is EngineEvent.Road -> {
                val at = e.at ?: continue
                val type = when (e.event.type) { RoadEventType.POTHOLE -> "pothole"; RoadEventType.HARD_BRAKE -> "hard_brake" }
                app.db.queueCrowd(type, at.lat, at.lon, e.event.severity, System.currentTimeMillis() / 1000)
                if (e.event.type == RoadEventType.HARD_BRAKE) tripId?.let { app.db.bumpTrip(it, "hard_brakes") }
                app.prefs.points = app.prefs.points + 1
            }
            is EngineEvent.Crash -> onCrash(e.event, e.at)
            is EngineEvent.DriveChanged -> {
                DriveBus.update { it.copy(driveState = e.state) }
                if (e.state == DriveState.STOPPED) lifecycleScope.launch(Dispatchers.Main) { stopDriving() }
            }
        }
    }

    private fun raise(a: Alert) {
        val d = arbiter.submit(a)
        if (d is AlertArbiter.Decision.Suppressed) return
        val shadow = d is AlertArbiter.Decision.Shadowed
        val p = lastPoint
        lastAlertDbId = app.db.logAlert(tripId, a.type.name, a.level.name, a.confidence, p?.lat, p?.lon, System.currentTimeMillis(), shadow)
        tripId?.let { id ->
            when (a.type) {
                AlertType.SPEEDING -> app.db.bumpTrip(id, "speeding")
                AlertType.FATIGUE -> app.db.bumpTrip(id, "fatigue")
                AlertType.PHONE_DISTRACTION -> app.db.bumpTrip(id, "distraction")
                else -> Unit
            }
        }
        if (shadow) return
        lastAlertText = AlertText.of(a)
        DriveBus.update { it.copy(lastAlert = a) }
        when (d) {
            is AlertArbiter.Decision.Play -> speak(d.alert)
            is AlertArbiter.Decision.Interrupt -> { voice.stop(); speak(d.alert) }
            else -> Unit
        }
    }

    /**
     * FR-11: namoyish safari. Farg'ona shahrida shimolga to'g'ri yo'l: 350 m da maktab (30 km/soat),
     * 600 m da piyodalar o'tish joyi, 1300 m da fotoradar (60 km/soat), 1650 m da "lejachiy", 1900 m da chuqur.
     * Tezlik ataylab cheklovdan oshiriladi — tezlik ogohlantirishi ham eshitilsin.
     */
    private fun startDemo() {
        demoJob?.cancel()
        val origin = LatLon(40.3790, 71.7860)
        fun at(m: Double) = Geo.destination(origin, 0.0, m)
        demoPois = listOf(
            Poi("demo-school", uz.hamroh.core.model.PoiType.SCHOOL, at(350.0).lat, at(350.0).lon, speedLimitKmh = 30, name = "Maktab (namoyish)"),
            Poi("demo-cross", uz.hamroh.core.model.PoiType.CROSSWALK, at(600.0).lat, at(600.0).lon),
            Poi("demo-cam", uz.hamroh.core.model.PoiType.SPEED_CAMERA, at(1300.0).lat, at(1300.0).lon, speedLimitKmh = 60, directionDeg = 0.0),
            Poi("demo-bump", uz.hamroh.core.model.PoiType.SPEED_BUMP, at(1650.0).lat, at(1650.0).lon),
            Poi("demo-hole", uz.hamroh.core.model.PoiType.POTHOLE, at(1900.0).lat, at(1900.0).lon),
        )
        DriveBus.update { it.copy(poiStatus = T("Namoyish safari", "Демо-поездка", "Demo drive")) }
        demoJob = lifecycleScope.launch(engineDispatcher) {
            var pos = 0.0
            var speed = 0.0
            var t = 0
            while (isActive && pos < 2_150.0) {
                val target = when {
                    t < 3 -> 0.0
                    pos < 250 -> 40.0
                    pos < 700 -> 42.0   // maktab hududida 30 dan yuqori — tezlik ogohlantirishi
                    pos < 1_550 -> 80.0 // radar oldida 60 dan yuqori
                    else -> 40.0
                }
                speed += (target - speed).coerceIn(-12.0, 8.0)
                pos += speed / 3.6
                val p = at(pos)
                onFix(LocationFix(SystemClock.elapsedRealtime(), p.lat, p.lon, speed / 3.6, if (speed > 3) 0.0 else null, 4.0))
                t++
                delay(1_000)
            }
            delay(3_000)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                toastAlert(T("Namoyish safari tugadi", "Демо-поездка завершена", "Demo drive finished"))
                stopDriving()
            }
        }
    }

    private fun speak(a: Alert) {
        voice.play(AlertText.voiceKey(a), a.level) {
            lifecycleScope.launch(engineDispatcher) {
                arbiter.onFinished(SystemClock.elapsedRealtime())?.let { next -> speak(next) }
            }
        }
    }

    private fun onCrash(e: CrashSosMachine.Event, at: LatLon?) {
        when (e) {
            is CrashSosMachine.Event.ProtectClip -> dashcam?.protect("impact")
            is CrashSosMachine.Event.CountdownStarted -> {
                raise(Alert(AlertType.SOS_COUNTDOWN, AlertLevel.URGENT, e.timeMs, "sos"))
                showSosScreen()
            }
            is CrashSosMachine.Event.SosTriggered -> {
                NotificationManagerCompat.from(this).cancel(SOS_NOTIF_ID)
                sendSos(e.peakG, at)
                engine.resetSos()
            }
            is CrashSosMachine.Event.Cancelled -> {
                NotificationManagerCompat.from(this).cancel(SOS_NOTIF_ID)
                voice.play("sos_cancelled", AlertLevel.VOICE) {}
            }
            is CrashSosMachine.Event.PhoneDropped -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSosScreen() {
        val full = PendingIntent.getActivity(this, 3, Intent(this, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 4, intent(this, "").setAction(ACTION_SOS_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, HamrohApp.CH_SOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(T("Kuchli zarba sezildi", "Обнаружен сильный удар", "Strong impact detected"))
            .setContentText(T("30 soniyada yaqinlaringizga xabar yuboriladi", "Через 30 секунд близким будет отправлено сообщение", "Family will be notified in 30 seconds"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .addAction(0, T("Men yaxshiman", "Я в порядке", "I'm OK"), cancel)
            .build()
        if (Build.VERSION.SDK_INT < 33 || hasPerm(Manifest.permission.POST_NOTIFICATIONS)) NotificationManagerCompat.from(this).notify(SOS_NOTIF_ID, n)
        runCatching { startActivity(Intent(this, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun sendSos(peakG: Double, at: LatLon?) {
        lifecycleScope.launch(Dispatchers.IO) {
            var delivered = 0
            runCatching { delivered += app.api.sos(at?.lat, at?.lon, peakG, System.currentTimeMillis() / 1000) }
                .onFailure { Log.w(TAG, "SOS server", it) }
            // FR-7.2: SMS zaxira (internet bo'lmasa ham)
            if (hasPerm(Manifest.permission.SEND_SMS)) {
                val phones = app.prefs.sosPhones.split(',').map { it.trim() }.filter { it.length >= 9 }
                val text = T("Hamroh AI: avariya ehtimoli, haydovchi javob bermadi.", "Hamroh AI: возможна авария, водитель не отвечает.", "Hamroh AI: possible crash, driver not responding.") +
                    (at?.let { " https://maps.google.com/?q=${it.lat},${it.lon}" } ?: "")
                val sms = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                for (ph in phones) runCatching {
                    sms.sendMultipartTextMessage(ph, null, sms.divideMessage(text), null, null); delivered++
                }
            }
            if (delivered > 0) voice.play("sos_sent", AlertLevel.URGENT) {}
            DriveBus.update { it.copy(lastAlert = Alert(AlertType.SOS_COUNTDOWN, AlertLevel.URGENT, SystemClock.elapsedRealtime(), "sos-sent", mapOf("delivered" to delivered.toString()))) }
        }
    }

    private fun markFalseAlert() {
        val id = lastAlertDbId ?: return
        lifecycleScope.launch(Dispatchers.IO) { app.db.setFeedback(id, false) }
        toastAlert(T("Rahmat, belgilandi", "Спасибо, отмечено", "Thanks, noted"))
    }

    private fun toastAlert(text: String) {
        lastAlertText = text
        lifecycleScope.launch(Dispatchers.Main) { android.widget.Toast.makeText(this@DriveService, text, android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun startLiveTrip() {
        val p = lastPoint ?: run { toastAlert(T("GPS kutilmoqda", "Ожидание GPS", "Waiting for GPS")); return }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val t = app.api.createLiveTrip(p.lat, p.lon)
                liveTrip = t
                DriveBus.update { it.copy(liveTripUrl = t.url) }
            } catch (e: Exception) {
                toastAlert(T("Server bilan aloqa yo'q", "Нет связи с сервером", "No server connection"))
            }
        }
    }

    // ---------------- kuzatuv rejimi (FR-4.9) ----------------

    private fun updateShadowMode() {
        // Yo'lda ovozli ogohlantirishlar 100% darhol va to'liq eshitilishi uchun soyali rejim o'chirilgan
        arbiter.shadowTypes = emptySet()
        DriveBus.update { it.copy(shadowMode = false) }
    }

    // ---------------- issiqlik (TZ 4-bo'lim) ----------------

    private var thermalListener: Any? = null

    private fun registerThermal() {
        if (Build.VERSION.SDK_INT < 29) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val l = PowerManager.OnThermalStatusChangedListener { status ->
            val level = when (status) {
                PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> 0
                PowerManager.THERMAL_STATUS_MODERATE -> 2
                PowerManager.THERMAL_STATUS_SEVERE -> 3
                else -> 4
            }
            val before = thermalLevel
            thermalLevel = level
            if (level >= 3 && before < 3) lifecycleScope.launch(engineDispatcher) {
                raise(Alert(AlertType.THERMAL, AlertLevel.VOICE, SystemClock.elapsedRealtime(), "thermal"))
            }
        }
        pm.addThermalStatusListener(ContextCompat.getMainExecutor(this), l)
        thermalListener = l
    }

    private fun unregisterThermal() {
        if (Build.VERSION.SDK_INT < 29) return
        val l = thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
        (getSystemService(POWER_SERVICE) as PowerManager).removeThermalStatusListener(l)
        thermalListener = null
    }

    // ---------------- yordamchilar ----------------

    private fun openTrackWriter() {
        val dir = File(filesDir, "tracks").apply { mkdirs() }
        val f = File(dir, "trip_${System.currentTimeMillis()}.csv")
        trackWriter = f.bufferedWriter().also { w ->
            val dt = LocalDateTime.now()
            w.write("# Hamroh AI trek yozuvi ${dt}\nCLOCK,${dt.dayOfWeek.value},${dt.hour * 60 + dt.minute}\n")
        }
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, intent(this, "").setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val falseAlert = PendingIntent.getService(this, 2, intent(this, "").setAction(ACTION_FALSE_ALERT), PendingIntent.FLAG_IMMUTABLE)
        val protect = PendingIntent.getService(this, 5, intent(this, "").setAction(ACTION_PROTECT), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, HamrohApp.CH_DRIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, T("To'xtatish", "Стоп", "Stop"), stop)
            .addAction(0, T("Noto'g'ri signal", "Ложный сигнал", "False alert"), falseAlert)
            .addAction(0, T("Klipni saqlash", "Сохранить клип", "Save clip"), protect)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm(Manifest.permission.POST_NOTIFICATIONS)) return
        val ui = DriveBus.state.value
        val text = "${ui.speedKmh.toInt()} km/h" + (ui.limitKmh?.let { " · ${T("cheklov", "лимит", "limit")} $it" } ?: "") +
            (lastAlertText?.let { " · $it" } ?: "")
        NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(T("Hamroh yoqilgan", "Hamroh включён", "Hamroh is on"), text))
    }

    companion object {
        private const val TAG = "DriveService"
        const val NOTIF_ID = 4001
        const val SOS_NOTIF_ID = 4002
        const val EXTRA_REASON = "reason"
        const val ACTION_STOP = "uz.hamroh.STOP"
        const val ACTION_SOS_CANCEL = "uz.hamroh.SOS_CANCEL"
        const val ACTION_FALSE_ALERT = "uz.hamroh.FALSE_ALERT"
        const val ACTION_PROTECT = "uz.hamroh.PROTECT"
        const val ACTION_CAR_DISCONNECTED = "uz.hamroh.CAR_DISCONNECTED"
        const val ACTION_LIVE_TRIP = "uz.hamroh.LIVE_TRIP"
        const val ACTION_FORCE_CAMERA = "uz.hamroh.FORCE_CAMERA"
        const val ACTION_REFRESH_SETTINGS = "uz.hamroh.REFRESH"
        const val ACTION_DEMO = "uz.hamroh.DEMO"

        @Volatile var instance: DriveService? = null
            private set

        fun intent(context: Context, reason: String) = Intent(context, DriveService::class.java).putExtra(EXTRA_REASON, reason)

        fun start(context: Context, reason: String) {
            ContextCompat.startForegroundService(context, intent(context, reason))
        }

        /** Faqat xizmat ishlayotgan bo'lsa buyruq yuboradi. */
        fun signal(context: Context, action: String) {
            if (instance == null) return
            runCatching { context.startService(Intent(context, DriveService::class.java).setAction(action)) }
        }

        fun stop(context: Context) = signal(context, ACTION_STOP)

        /** FR-11: namoyish safarini boshlaydi (xizmat ishlamayotgan bo'lsa ham). */
        fun startDemo(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, "demo").setAction(ACTION_DEMO))
        }

        fun parseSchedule(csv: String): SchoolSchedule {
            val ranges = csv.split(',').mapNotNull { part ->
                val m = Regex("(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})").matchEntire(part.trim()) ?: return@mapNotNull null
                val (h1, m1, h2, m2) = m.destructured
                MinuteRange(h1.toInt() * 60 + m1.toInt(), h2.toInt() * 60 + m2.toInt())
            }
            return if (ranges.isEmpty()) SchoolSchedule() else SchoolSchedule(ranges)
        }
    }
}
