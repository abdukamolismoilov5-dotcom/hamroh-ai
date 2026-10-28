package uz.hamroh.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.data.SyncWorker
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.drive.DriveService
import uz.hamroh.app.drive.VoicePlayer
import uz.hamroh.app.util.AlertText
import uz.hamroh.app.util.T
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.PoiType
import uz.hamroh.core.safety.CrashSosMachine
import uz.hamroh.core.vision.ObjClass

@Composable
fun DriveScreen(app: HamrohApp) {
    val ui by DriveBus.state.collectAsState()
    val ctx = LocalContext.current
    var showCamera by remember { mutableStateOf(true) }
    var reportOpen by remember { mutableStateOf(false) }
    var gasOpen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var activeVoiceChip by remember { mutableStateOf<String?>(null) }

    val localVoice = remember { VoicePlayer(ctx) }
    DisposableEffect(Unit) {
        onDispose { localVoice.release() }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        when (pendingAction) {
            "camera", "manual" -> {
                app.prefs.liteMode = false
                if (app.prefs.cameraMode == "off") app.prefs.cameraMode = "road"
                showCamera = true
                if (!ui.running) DriveService.start(ctx, "camera")
                else DriveService.signal(ctx, DriveService.ACTION_REFRESH_SETTINGS)
            }
            "demo" -> {
                app.prefs.liteMode = false
                if (app.prefs.cameraMode == "off") app.prefs.cameraMode = "road"
                showCamera = true
                DriveService.startDemo(ctx)
            }
        }
        pendingAction = null
    }

    fun ensurePermsAndRun(action: String) {
        val hasCam = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasLoc = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCam || !hasLoc) {
            pendingAction = action
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        } else {
            when (action) {
                "camera", "manual" -> {
                    app.prefs.liteMode = false
                    if (app.prefs.cameraMode == "off") app.prefs.cameraMode = "road"
                    showCamera = true
                    if (!ui.running) DriveService.start(ctx, "camera")
                    else DriveService.signal(ctx, DriveService.ACTION_REFRESH_SETTINGS)
                }
                "demo" -> {
                    app.prefs.liteMode = false
                    if (app.prefs.cameraMode == "off") app.prefs.cameraMode = "road"
                    showCamera = true
                    DriveService.startDemo(ctx)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- 1. TOP CYBER COCKPIT HEADER BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HColors.Surface)
                .border(1.dp, HColors.Border, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OfficialLogoBadge(size = 42.dp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "HAMROH AI",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (app.prefs.isSubscribed) Color.White else HColors.SurfaceHi)
                                .border(1.dp, if (app.prefs.isSubscribed) Color.White else HColors.Accent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (app.prefs.isSubscribed) "💎 PRO" else "AI v2.5",
                                color = if (app.prefs.isSubscribed) Color.Black else HColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    Text(
                        if (app.prefs.userName.isNotBlank()) "👤 ${app.prefs.userName} ${app.prefs.userSurname}".trim()
                        else T("Intellektual yo'l xavfsizligi tizimi", "Интеллектуальная система безопасности", "Intelligent Road Safety Cockpit"),
                        color = HColors.TextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Status telemetry pills
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (ui.gpsOk) "GPS ✓" else "GPS", ui.gpsOk)
                StatusPill(
                    if (ui.cameraStatus == "road") "AI %.0ffps".format(java.util.Locale.US, ui.visionFps.coerceAtLeast(1f))
                    else if (ui.cameraStatus == "driver") "FACE AI"
                    else "CAM",
                    ui.cameraStatus == "road" || ui.cameraStatus == "driver"
                )
                if (ui.dashcamRecording) {
                    StatusPill("● REC", true, isDanger = true)
                }
            }
        }

        // --- 2. HERO DIGITAL SPEEDOMETER & TELEMETRY CARD ---
        val overSpeed = ui.limitKmh != null && ui.speedKmh > ui.limitKmh!! + 5
        val liveObjectsCount = ui.risks.count { it.isLive }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HColors.SurfaceHi,
                            HColors.Surface,
                        )
                    )
                )
                .border(
                    width = if (overSpeed) 2.dp else 1.dp,
                    color = if (overSpeed) HColors.Danger else HColors.Border,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Speed readout + Road Sign Limit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "${ui.speedKmh.toInt()}",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            color = if (overSpeed) HColors.Danger else Color.White,
                            lineHeight = 72.sp,
                        )
                        Text(
                            text = T("KM / SOAT", "КМ / ЧАС", "KM / H"),
                            color = HColors.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    // Road speed limit sign (shows active limit or default 60 urban indicator)
                    val displayLimit = ui.limitKmh ?: 60
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(5.dp, HColors.Danger, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$displayLimit",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                "LIMIT",
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Bold,
                                fontSize = 7.sp,
                            )
                        }
                    }
                }

                // Right: 3 Telemetry Metrics
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    TelemetryMiniRow(
                        label = T("MASOFA", "ПУТЬ", "DIST"),
                        value = "%.1f km".format(java.util.Locale.US, ui.tripDistanceKm),
                        accent = Color.White,
                    )
                    TelemetryMiniRow(
                        label = T("VAQT", "ВРЕМЯ", "TIME"),
                        value = "${ui.continuousDrivingMin} ${T("daq", "мин", "min")}",
                        accent = Color.White,
                    )
                    TelemetryMiniRow(
                        label = T("OBYEKTLAR", "ОБЪЕКТЫ", "OBJECTS"),
                        value = "$liveObjectsCount ${T("jonli", "живых", "live")} · ${ui.nearbyPoiCount} POI",
                        accent = if (liveObjectsCount > 0) HColors.Accent else HColors.TextDim,
                    )
                }
            }
        }

        // --- 3. SCHOOL ZONE & ACTIVE VOICE ALERT BANNERS ---
        if (ui.inSchoolZone) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF59E0B))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🚸", fontSize = 22.sp)
                Column {
                    Text(
                        if (ui.schoolEnhanced) T("MAKTAB HUDUDI · DARS VAQTI!", "ШКОЛЬНАЯ ЗОНА · УРОКИ!", "SCHOOL ZONE · ACTIVE HOURS!")
                        else T("DIQQAT: MAKTAB HUDUDI", "ВНИМАНИЕ: ШКОЛЬНАЯ ЗОНА", "CAUTION: SCHOOL ZONE"),
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                    )
                    Text(
                        T("Bolalar yo'lga chiqishi mumkin — tezlikni pasaytiring", "Дети могут выйти на дорогу — снизьте скорость", "Children may cross — reduce speed"),
                        color = Color(0xFF1E293B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        ui.lastAlert?.let { a ->
            val danger = a.level == AlertLevel.URGENT
            val text = if (a.dedupKey == "sos-sent")
                T("SOS: ${a.args["delivered"]} ta yaqiningizga xabar yuborildi", "SOS: отправлено ${a.args["delivered"]} близким", "SOS: ${a.args["delivered"]} contacts notified")
            else AlertText.of(a)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (danger) HColors.Danger else HColors.SurfaceHi)
                    .border(1.5.dp, if (danger) Color.White else HColors.Accent, RoundedCornerShape(14.dp))
                    .clickable {
                        localVoice.play(AlertText.voiceKey(a), AlertLevel.URGENT) {}
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (danger) "🚨" else "🔊", fontSize = 22.sp)
                    Column {
                        Text(
                            text,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            T("Qayta eshitish uchun bosing", "Нажмите для повтора озвучки", "Tap to replay voice alert"),
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("UZ AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 4. START / LAUNCH PANEL (WHEN NOT RUNNING) ---
        if (!ui.running) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HColors.Surface)
                    .border(1.dp, HColors.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { ensurePermsAndRun("camera") },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text(
                        T("🚀 HAYDASH VA JONLI AI KAMERANI BOSHLASH", "🚀 НАЧАТЬ ПОЕЗДКУ И ЖИВУЮ ИИ-КАМЕРУ", "🚀 START DRIVE & LIVE AI CAMERA"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { ensurePermsAndRun("camera") },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HColors.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HColors.Accent),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            T("🎥 Jonli Kamera AI", "🎥 Живая Камера ИИ", "🎥 Live AI Camera"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    OutlinedButton(
                        onClick = { ensurePermsAndRun("demo") },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            T("🎮 Namoyish (Simulyatsiya)", "🎮 Демо-поездка", "🎮 Demo Simulation"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    T(
                        "✨ Kamera faqat haqiqiy jonli harakatdagi piyoda va mashinalarni oladi (plakat/rasmlarni va o'z kapotingizni tashlab ketadi).",
                        "✨ Камера распознаёт только реальных живых пешеходов и машины (игнорирует плакаты и капот).",
                        "✨ Camera detects only real live pedestrians & vehicles (ignores static posters and your own car hood)."
                    ),
                    color = HColors.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        // --- 5. LIVE AI CAMERA COCKPIT VIEWPORT ---
        if (ui.running) {
            // Single-line sleek mode switcher buttons (no awkward text wrapping!)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (!showCamera) ensurePermsAndRun("camera")
                        else showCamera = false
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HColors.Surface,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        if (showCamera) T("👁️ Kamerani yashirish", "👁️ Скрыть камеру", "👁️ Hide camera")
                        else T("👁️ Kamerani ochish", "👁️ Показать камеру", "👁️ Show camera"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = {
                        app.prefs.liteMode = false
                        app.prefs.cameraMode = if (app.prefs.cameraMode == "driver") "road" else "driver"
                        showCamera = true
                        DriveService.signal(ctx, DriveService.ACTION_REFRESH_SETTINGS)
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HColors.Accent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HColors.Surface,
                        contentColor = HColors.Accent,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        if (ui.cameraStatus == "driver") T("🔄 Rejim: Charchoq (Old)", "🔄 Режим: Усталость", "🔄 Mode: Fatigue")
                        else T("🔄 Rejim: Yo'l (Orqa)", "🔄 Режим: Дорога", "🔄 Mode: Road AI"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (showCamera) {
                CameraPreview(app)
            }
        }

        // --- 6. PURE UZBEK AI VOICE SHOWCASE BAR (1-TAP LIVE AUDIO TEST) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HColors.Surface)
                .border(1.dp, HColors.Border, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎙️", fontSize = 15.sp)
                    Text(
                        T("Sof O'zbek tilidagi AI ovozlar (Bosing va eshiting)", "Голосовые оповещения ИИ (Нажмите для прослушивания)", "Pure Uzbek Neural Voice Alerts (Tap to test)"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "HD OVOZ",
                    color = HColors.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            val voiceSamples = listOf(
                Triple("drive_started", "🔊 Salomlashuv", "Hamroh xavfsizlik tizimi ishga tushdi"),
                Triple("forward_collision", "🚗 Yaqin mashina", "Diqqat, xavf! Oldingi avtomobilga juda yaqinsiz"),
                Triple("child_ahead", "🚸 Bola xavfi", "Diqqat! Yo'lda yosh bola bo'lishi mumkin"),
                Triple("pedestrian_ahead", "🚶 Piyoda", "Ehtiyot bo'ling! Oldinda piyoda"),
                Triple("speed_camera", "📸 Fotoradar", "Oldinda tezlikni nazorat qiluvchi radar bor"),
                Triple("school_zone_enhanced", "🏫 Maktab hududi", "Diqqat, maktab hududi! Hozir dars vaqti"),
                Triple("fatigue_rest", "😴 Charchoq", "Sizda charchoq alomatlari sezilmoqda"),
                Triple("speeding", "⚠️ Tezlik oshdi", "Diqqat! Tezlik belgilangan me'yordan oshdi"),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                voiceSamples.forEach { (key, title, _) ->
                    val isPlayingThis = activeVoiceChip == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPlayingThis) Color.White else HColors.SurfaceHi)
                            .border(
                                1.dp,
                                if (isPlayingThis) Color.White else HColors.Border,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                activeVoiceChip = key
                                localVoice.play(key, AlertLevel.URGENT) {
                                    if (activeVoiceChip == key) activeVoiceChip = null
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = if (isPlayingThis) "🔊 $title..." else title,
                            color = if (isPlayingThis) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // --- 7. TACTICAL ACTION GRID (WHEN RUNNING) ---
        if (ui.running) {
            if (ui.sosState == CrashSosMachine.State.COUNTDOWN) {
                Button(
                    onClick = { DriveService.signal(ctx, DriveService.ACTION_SOS_CANCEL) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HColors.Danger),
                ) {
                    Text(
                        T("🚨 MEN YAXSHIMAN — SOS BEKOR QILISH (${ui.sosRemainingS}s)", "🚨 Я В ПОРЯДКЕ (${ui.sosRemainingS}с)", "🚨 I'M OK — CANCEL SOS (${ui.sosRemainingS}s)"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { reportOpen = true },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        T("⚠️ Xabar berish", "⚠️ Сообщить", "⚠️ Report Hazard"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = { gasOpen = true },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HColors.SurfaceHi, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        T("⛽ Metan navbati", "⛽ Метан очередь", "⛽ CNG Stations"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { DriveService.signal(ctx, DriveService.ACTION_PROTECT) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = HColors.Surface, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        T("💾 Klipni saqlash", "💾 Сохранить клип", "💾 Save Clip"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val url = ui.liveTripUrl
                        if (url == null) DriveService.signal(ctx, DriveService.ACTION_LIVE_TRIP)
                        else ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null))
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = HColors.Surface, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        if (ui.liveTripUrl == null) T("📡 Jonli safar", "📡 Живая поездка", "📡 Live Trip")
                        else T("📤 Havolani ulashish", "📤 Поделиться", "📤 Share Link"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { DriveService.signal(ctx, DriveService.ACTION_FALSE_ALERT) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = HColors.Surface, contentColor = HColors.TextDim),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        T("🔕 Xato signal", "🔕 Ложный сигнал", "🔕 False Alert"),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                OutlinedButton(
                    onClick = { DriveService.stop(ctx) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HColors.Danger),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x22FF2E4C), contentColor = HColors.Danger),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        T("⏹ To'xtatish", "⏹ Остановить", "⏹ Stop Drive"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    if (reportOpen) ReportDialog(app, LatLon(ui.lat ?: 0.0, ui.lon ?: 0.0), ui.lat != null) { reportOpen = false }
    if (gasOpen) GasDialog(app, ui.lat, ui.lon) { gasOpen = false }
}

@Composable
private fun StatusPill(text: String, active: Boolean, isDanger: Boolean = false) {
    val bg = when {
        isDanger -> Color(0x33FF2E4C)
        active -> HColors.SurfaceHi
        else -> HColors.Bg
    }
    val borderCol = when {
        isDanger -> HColors.Danger
        active -> HColors.Accent
        else -> HColors.Border
    }
    val txtCol = when {
        isDanger -> HColors.Danger
        active -> Color.White
        else -> HColors.TextDim
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = txtCol, maxLines = 1)
    }
}

@Composable
private fun TelemetryMiniRow(label: String, value: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = HColors.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(HColors.Bg)
                .border(1.dp, HColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

private fun detLabel(cls: ObjClass): String = when (cls) {
    ObjClass.PERSON -> T("Piyoda", "Пешеход", "Pedestrian")
    ObjClass.BIKE -> T("Velosiped", "Вело/Мото", "Bike/Moto")
    ObjClass.CAR -> T("Avtomobil", "Автомобиль", "Car")
    ObjClass.BUS_TRUCK -> T("Yuk/Avtobus", "Грузовик/Автобус", "Bus/Truck")
}

/** Kamera oqimi + Cyber HUD burchaklari + aniqlangan jonli obyektlar qutilari. */
@Composable
private fun CameraPreview(app: HamrohApp) {
    val ui by DriveBus.state.collectAsState()
    val ctx = LocalContext.current
    val view = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val pillPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(215, 8, 8, 14)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
    val pillStrokePaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
    }

    LaunchedEffect(ui.running, ui.cameraStatus) {
        if (app.prefs.cameraMode == "off") {
            app.prefs.cameraMode = "road"
            DriveService.signal(ctx, DriveService.ACTION_REFRESH_SETTINGS)
        }
        var tries = 0
        while (DriveService.instance == null && tries < 20) {
            delay(100)
            tries++
        }
        DriveService.instance?.attachPreview(view.surfaceProvider)
    }
    DisposableEffect(Unit) {
        DriveService.instance?.attachPreview(view.surfaceProvider)
        onDispose { DriveService.instance?.attachPreview(null) }
    }

    val liveRisks = ui.risks.filter { it.isLive }
    val hasDanger = liveRisks.any { it.inCorridor || it.entering }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(
                width = if (hasDanger) 2.dp else 1.dp,
                color = if (hasDanger) HColors.Danger else HColors.Border,
                shape = RoundedCornerShape(16.dp),
            )
    ) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val hy = ui.horizonY * size.height
            // Gorizont chizig'i
            drawLine(Color(0x6638BDF8), Offset(0f, hy), Offset(size.width, hy), strokeWidth = 2f)

            // Harakat koridori chiziqlari (Cyber Lane Corridor)
            drawLine(
                Color(0x66E2E8F0),
                Offset(size.width * 0.42f, hy),
                Offset(size.width * 0.14f, size.height),
                strokeWidth = 3f,
            )
            drawLine(
                Color(0x66E2E8F0),
                Offset(size.width * 0.58f, hy),
                Offset(size.width * 0.86f, size.height),
                strokeWidth = 3f,
            )

            // Cyber HUD Corner Brackets
            val bracketLen = 28f
            val m = 16f
            val hudCol = if (hasDanger) Color(0xFFFF2E4C) else Color(0xaa38BDF8)
            // Top-Left
            drawLine(hudCol, Offset(m, m), Offset(m + bracketLen, m), strokeWidth = 4f)
            drawLine(hudCol, Offset(m, m), Offset(m, m + bracketLen), strokeWidth = 4f)
            // Top-Right
            drawLine(hudCol, Offset(size.width - m, m), Offset(size.width - m - bracketLen, m), strokeWidth = 4f)
            drawLine(hudCol, Offset(size.width - m, m), Offset(size.width - m, m + bracketLen), strokeWidth = 4f)
            // Bottom-Left
            drawLine(hudCol, Offset(m, size.height - m), Offset(m + bracketLen, size.height - m), strokeWidth = 4f)
            drawLine(hudCol, Offset(m, size.height - m), Offset(m, size.height - m - bracketLen), strokeWidth = 4f)
            // Bottom-Right
            drawLine(hudCol, Offset(size.width - m, size.height - m), Offset(size.width - m - bracketLen, size.height - m), strokeWidth = 4f)
            drawLine(hudCol, Offset(size.width - m, size.height - m), Offset(size.width - m, size.height - m - bracketLen), strokeWidth = 4f)

            // Draw bounding boxes for live objects (sorted by closest distance)
            val sortedRisks = liveRisks.sortedBy { it.distanceM }
            sortedRisks.forEachIndexed { idx, r ->
                val b = r.track.box
                val danger = r.inCorridor || r.entering
                val boxColor = if (danger) Color(0xFFFF2E4C) else Color(0xFF38BDF8)
                val left = (b.left * size.width).coerceIn(4f, size.width - 20f)
                val top = (b.top * size.height).coerceIn(4f, size.height - 20f)
                val w = (b.width * size.width).coerceAtLeast(16f)
                val h = (b.height * size.height).coerceAtLeast(16f)

                drawRoundRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = if (danger) 5.5f else 3.5f),
                )

                // Draw clean pill label for top 3 closest objects to avoid clutter
                if (idx < 3) {
                    val label = buildString {
                        if (r.likelyChild) append("🚸 Bola ") else append(detLabel(r.track.cls)).append(" ")
                        append("%.0fm".format(java.util.Locale.US, r.distanceM))
                        r.ttcS?.let { t -> if (t < 9.9) append(" · %.1fs".format(java.util.Locale.US, t)) }
                    }
                    val textW = textPaint.measureText(label)
                    val pillLeft = left.coerceIn(10f, (size.width - textW - 28f).coerceAtLeast(10f))
                    val pillTop = if (top > 48f) top - 38f else (top + 8f)
                    val rect = RectF(pillLeft, pillTop, pillLeft + textW + 20f, pillTop + 34f)
                    pillStrokePaint.color = if (danger) android.graphics.Color.rgb(255, 46, 76) else android.graphics.Color.rgb(56, 189, 248)
                    drawContext.canvas.nativeCanvas.drawRoundRect(rect, 8f, 8f, pillPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(rect, 8f, 8f, pillStrokePaint)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        pillLeft + 10f,
                        pillTop + 25f,
                        textPaint,
                    )
                }
            }
        }

        // Yuqori jonli holat paneli
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0xCC050508), RoundedCornerShape(8.dp))
                .border(1.dp, HColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .background(if (hasDanger) HColors.Danger else Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (hasDanger) "XAVF / DANGER" else "JONLI AI",
                    color = if (hasDanger) Color.White else Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            val statusTxt = when (ui.cameraStatus) {
                "road" -> "%.0f FPS · %d %s".format(java.util.Locale.US, ui.visionFps.coerceAtLeast(1f), liveRisks.size, T("jonli obyekt", "объектов", "live objects"))
                "driver" -> {
                    val earTxt = ui.earValue?.let { "EAR %.2f".format(java.util.Locale.US, it) } ?: T("Yuz qidirilmoqda…", "Поиск лица…", "Scanning face…")
                    "${T("Haydovchi nazorati", "Контроль водителя", "Driver AI")} · $earTxt"
                }
                else -> T("Kamera AI faollashmoqda…", "Запуск ИИ камеры…", "Starting AI camera…")
            }
            Text(statusTxt, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Pastki eng yaqin obyekt ma'lumoti
        val closestRisk = liveRisks.minByOrNull { it.distanceM }
        if (closestRisk != null) {
            val isCloseDanger = closestRisk.inCorridor || closestRisk.entering
            val bottomInfo = "${detLabel(closestRisk.track.cls)}: %.1f m".format(java.util.Locale.US, closestRisk.distanceM) +
                (closestRisk.ttcS?.let { t -> " · TTC %.1f s".format(java.util.Locale.US, t) } ?: "")
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(if (isCloseDanger) Color(0xDDFF2E4C) else Color(0xCC050508), RoundedCornerShape(8.dp))
                    .border(1.dp, if (isCloseDanger) Color.White else HColors.Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (isCloseDanger) "⚠️" else "🎯", fontSize = 12.sp)
                Text(
                    bottomInfo,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReportDialog(app: HamrohApp, at: LatLon, hasFix: Boolean, close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val types = listOf(
        "report_camera" to T("📸 Radar / kamera", "📸 Радар / камера", "📸 Speed camera"),
        "report_crash" to T("💥 YTH (Avariya)", "💥 ДТП", "💥 Crash"),
        "report_roadwork" to T("🚧 Yo'l ta'miri", "🚧 Ремонт дороги", "🚧 Road works"),
        "report_police" to T("🚓 YHXB posti", "🚓 Пост ДПС", "🚓 Police post"),
        "report_closed" to T("⛔ Yo'l yopiq", "⛔ Дорога закрыта", "⛔ Road closed"),
        "pothole" to T("🕳️ Chuqur", "🕳️ Яма", "🕳️ Pothole"),
    )
    AlertDialog(
        onDismissRequest = close,
        containerColor = HColors.Surface,
        title = { Text(T("Yo'lda nima bor?", "Что на дороге?", "Report Road Hazard"), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!hasFix) Text(T("GPS hali aniqlanmadi", "GPS ещё не определён", "No GPS fix yet"), color = HColors.Danger, fontSize = 12.sp)
                types.forEach { (code, label) ->
                    Button(
                        enabled = hasFix,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                app.db.queueCrowd(code, at.lat, at.lon, 0.0, System.currentTimeMillis() / 1000)
                                app.prefs.points = app.prefs.points + 2
                                SyncWorker.now(ctx)
                            }
                            android.widget.Toast.makeText(ctx, T("Rahmat! +2 ball qo'shildi", "Спасибо! +2 балла", "Thanks! +2 points"), android.widget.Toast.LENGTH_SHORT).show()
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HColors.SurfaceHi, contentColor = Color.White),
                    ) { Text(label, fontWeight = FontWeight.SemiBold) }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(T("Yopish", "Закрыть", "Close"), color = HColors.Accent) } },
    )
}

/** FR-3.5: metan/propan shoxobchasi holati va navbati. */
@Composable
private fun GasDialog(app: HamrohApp, lat: Double?, lon: Double?, close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stations by remember { mutableStateOf<List<uz.hamroh.core.model.Poi>>(emptyList()) }
    var server by remember { mutableStateOf<Map<String, ServerApi.GasStation>>(emptyMap()) }
    var chosen by remember { mutableStateOf<uz.hamroh.core.model.Poi?>(null) }
    var status by remember { mutableStateOf("open") }
    var queue by remember { mutableStateOf("0") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lat, lon) {
        if (lat == null || lon == null) return@LaunchedEffect
        val here = LatLon(lat, lon)
        stations = withContext(Dispatchers.IO) {
            app.db.poisNear(here, 5_000.0).filter { it.type == PoiType.GAS_STATION }
                .sortedBy { uz.hamroh.core.geo.Geo.distanceM(here, it.point) }.take(8)
        }
        server = withContext(Dispatchers.IO) {
            runCatching { app.api.gas(uz.hamroh.core.geo.Geo.bbox(here, 5_000.0)).associateBy { it.id } }.getOrDefault(emptyMap())
        }
    }
    AlertDialog(
        onDismissRequest = close,
        containerColor = HColors.Surface,
        title = { Text(T("⛽ Metan shoxobchalari", "⛽ Метановые заправки", "⛽ CNG stations"), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stations.isEmpty()) Text(T("Yaqin atrofda shoxobcha topilmadi (OSM)", "Рядом заправок нет (OSM)", "No stations nearby (OSM)"), color = HColors.TextDim)
                stations.forEach { s ->
                    val st = server[s.id]
                    val info = st?.let {
                        val q = statusLabel(it.status) + " · " + T("navbat", "очередь", "queue") + " " + it.queue
                        if (it.stale) "$q (${T("eskirgan", "устарело", "stale")})" else "$q · ${it.ageS / 60} ${T("daq", "мин", "min")}"
                    } ?: T("ma'lumot yo'q", "нет данных", "no data")
                    OutlinedButton(onClick = { chosen = s }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(s.name ?: T("Shoxobcha", "Заправка", "Station"), fontWeight = if (chosen == s) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                            Text(info, fontSize = 12.sp, color = HColors.TextDim)
                        }
                    }
                }
                chosen?.let { s ->
                    Text(T("Holat:", "Статус:", "Status:"), color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("open", "closed", "no_gas").forEach { v -> FilterChip(selected = status == v, onClick = { status = v }, label = { Text(statusLabel(v), fontSize = 12.sp) }) }
                    }
                    Text(T("Navbat (mashina):", "Очередь (машин):", "Queue (cars):"), color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("0", "1-5", "6-15", "15+").forEach { v -> FilterChip(selected = queue == v, onClick = { queue = v }, label = { Text(v) }) }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                message = withContext(Dispatchers.IO) {
                                    runCatching { app.api.putGas(s.id, s.lat, s.lon, s.name, status, queue); app.prefs.points = app.prefs.points + 3 }
                                        .fold({ T("Yuborildi, rahmat! +3 ball", "Отправлено, спасибо! +3", "Sent, thanks! +3") }, { T("Server bilan aloqa yo'q", "Нет связи с сервером", "No server connection") })
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Text(T("Yuborish", "Отправить", "Send"), fontWeight = FontWeight.Bold) }
                }
                message?.let { Text(it, color = HColors.Accent) }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(T("Yopish", "Закрыть", "Close"), color = HColors.Accent) } },
    )
}

private fun statusLabel(s: String) = when (s) {
    "open" -> T("ochiq", "открыто", "open")
    "closed" -> T("yopiq", "закрыто", "closed")
    else -> T("gaz yo'q", "нет газа", "no gas")
}
