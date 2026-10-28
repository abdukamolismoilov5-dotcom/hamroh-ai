package uz.hamroh.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.hamroh.app.BuildConfig
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.data.LocalDb
import uz.hamroh.app.drive.DriveService
import uz.hamroh.app.util.T
import uz.hamroh.core.care.DriverScore
import uz.hamroh.core.care.WeekStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoreScreen(app: HamrohApp, onLanguage: () -> Unit) {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                T("💎 Obuna (15k)", "💎 Подписка", "💎 Subscription"),
                T("👤 Profil / Kirish", "👤 Профиль", "👤 Profile / Auth"),
                T("Baho", "Оценка", "Score"),
                T("AI baholash", "Оценка ИИ", "AI review"),
                T("Kliplar", "Клипы", "Clips"),
                T("Sozlamalar", "Настройки", "Settings"),
                T("Ilova", "О прил.", "About")
            ).forEachIndexed { i, label ->
                FilterChip(selected = section == i, onClick = { section = i }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                0 -> SubscriptionSection(app)
                1 -> AuthSection(app)
                2 -> ScoreSection(app)
                3 -> ReviewSection(app)
                4 -> ClipsSection(app)
                5 -> SettingsSection(app, onLanguage)
                else -> AboutSection(app)
            }
        }
    }
}

// ---------------- FR-8.3 haftalik baho ----------------

@Composable
private fun ScoreSection(app: HamrohApp) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<WeekStats?>(null) }
    var trips by remember { mutableStateOf<List<LocalDb.TripRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        val since = System.currentTimeMillis() - 7L * 86_400_000
        stats = withContext(Dispatchers.IO) { app.db.weekStats(since) }
        trips = withContext(Dispatchers.IO) { app.db.recentTrips(10) }
    }
    val s = stats ?: return
    val score = DriverScore.compute(s)
    Card(title = T("Haftalik baho", "Оценка за неделю", "Weekly score")) {
        Text(score?.toString() ?: "—", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = HColors.Primary)
        if (score == null) Text(T("Baho uchun kamida 10 km haydang", "Нужно проехать хотя бы 10 км", "Drive at least 10 km to get a score"), color = HColors.TextDim)
        Text(T("Masofa: %.1f km · tezlik oshishi: %d · qattiq tormoz: %d · charchoq: %d", "Пробег: %.1f км · превышения: %d · резкие торможения: %d · усталость: %d",
            "Distance: %.1f km · speeding: %d · hard brakes: %d · fatigue: %d").format(Locale.US, s.distanceKm, s.speedingEvents, s.hardBrakes, s.fatigueAlerts))
        Text(T("Jamoaga hissa: ${app.prefs.points} ball", "Вклад в сообщество: ${app.prefs.points} баллов", "Community points: ${app.prefs.points}"), color = HColors.Accent)
        if (score != null) Button(onClick = { shareScoreCard(ctx, score, s) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(T("Telegram'ga ulashish", "Поделиться в Telegram", "Share to Telegram"))
        }
    }
    Card(title = T("Oxirgi safarlar", "Последние поездки", "Recent trips")) {
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.US)
        if (trips.isEmpty()) Text(T("Hali safar yo'q", "Поездок пока нет", "No trips yet"))
        trips.forEach { t ->
            Text("${fmt.format(Date(t.startedAt))} · %.1f km · max %.0f km/h".format(Locale.US, t.distanceM / 1000, t.maxSpeed), fontSize = 13.sp)
        }
    }
}

/** 1080×1350 rasm-kartochka (FR-8.3). */
private fun shareScoreCard(ctx: Context, score: Int, s: WeekStats) {
    val bmp = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.rgb(5, 5, 8))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    p.textSize = 64f; c.drawText("Hamroh AI", 80f, 160f, p)
    p.textSize = 42f; p.typeface = Typeface.DEFAULT; p.color = android.graphics.Color.rgb(148, 163, 184)
    c.drawText(T("Haftalik xavfsiz haydash bahosi", "Оценка безопасного вождения за неделю", "Weekly safe-driving score"), 80f, 240f, p)
    p.textSize = 360f; p.typeface = Typeface.DEFAULT_BOLD; p.color = android.graphics.Color.rgb(56, 189, 248)
    c.drawText("$score", 80f, 680f, p)
    p.textSize = 48f; p.typeface = Typeface.DEFAULT; p.color = android.graphics.Color.WHITE
    c.drawText("%.0f km".format(Locale.US, s.distanceKm), 80f, 820f, p)
    c.drawText(T("Tezlik oshishi: ${s.speedingEvents}", "Превышения: ${s.speedingEvents}", "Speeding: ${s.speedingEvents}"), 80f, 900f, p)
    c.drawText(T("Qattiq tormoz: ${s.hardBrakes}", "Резкие торможения: ${s.hardBrakes}", "Hard brakes: ${s.hardBrakes}"), 80f, 980f, p)
    p.color = android.graphics.Color.rgb(242, 194, 48); p.textSize = 40f
    c.drawText(T("Yo'lda bolalarni asraylik", "Берегите детей на дорогах", "Keep children safe on the road"), 80f, 1240f, p)
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "hamroh_score.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
}

// ---------------- FR-4.9 kuzatuv rejimi baholash ----------------

@Composable
private fun ReviewSection(app: HamrohApp) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<LocalDb.AlertRow>>(emptyList()) }
    var rate by remember { mutableStateOf(0 to 0) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        rows = withContext(Dispatchers.IO) { app.db.alertsForReview(60) }
        rate = withContext(Dispatchers.IO) { app.db.falseAlertRate() }
    }
    Card(title = T("Kamera AI aniqligi", "Точность камеры ИИ", "Camera AI accuracy")) {
        Text(T("Baholangan: ${rate.second}, noto'g'ri: ${rate.first}", "Оценено: ${rate.second}, ложных: ${rate.first}", "Rated: ${rate.second}, false: ${rate.first}"))
        Text(T("Har bir signal to'g'ri yoki noto'g'ri ekanini belgilang — bu O'zbekiston yo'llari uchun modelni yaxshilaydi.",
            "Отметьте каждый сигнал — это улучшит модель для дорог Узбекистана.",
            "Mark each signal — this improves the model for Uzbek roads."), color = HColors.TextDim, fontSize = 12.sp)
    }
    val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
    if (rows.isEmpty()) Text(T("Hali AI signallari yo'q", "Сигналов ИИ пока нет", "No AI signals yet"))
    rows.forEach { r ->
        Card {
            Text("${fmt.format(Date(r.ts))} · ${r.type}" + (r.confidence?.let { " · %.0f%%".format(Locale.US, it * 100) } ?: "") +
                if (r.shadow) " · " + T("ovozsiz", "без звука", "silent") else "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val fb = r.feedback
                OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { app.db.setFeedback(r.id, true) }; reload++ } }) {
                    Text((if (fb == 1) "● " else "") + T("To'g'ri", "Верно", "Correct"))
                }
                OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { app.db.setFeedback(r.id, false) }; reload++ } }) {
                    Text((if (fb == 0) "● " else "") + T("Noto'g'ri", "Ложно", "False"))
                }
            }
        }
    }
}

// ---------------- FR-6 kliplar ----------------

@Composable
private fun ClipsSection(app: HamrohApp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf<List<LocalDb.ClipRow>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { clips = withContext(Dispatchers.IO) { app.db.protectedClips() } }
    Text(T("Zarba yoki \"Klipni saqlash\" tugmasi bilan saqlangan videolar. Har biri yonida SHA-256 xeshli JSON bor — o'zgartirilmaganini isbotlash uchun.",
        "Видео, сохранённые при ударе или кнопкой «Сохранить клип». Рядом JSON с SHA-256 — доказательство неизменности.",
        "Videos saved on impact or with \"Save clip\". Each has a JSON with SHA-256 to prove it is unmodified."), color = HColors.TextDim, fontSize = 13.sp)
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)
    if (clips.isEmpty()) Text(T("Saqlangan klip yo'q", "Сохранённых клипов нет", "No saved clips"))
    clips.forEach { c ->
        Card {
            Text("${fmt.format(Date(c.startedAt))} · ${c.reason}", fontWeight = FontWeight.SemiBold)
            Text("SHA-256: ${c.sha256?.take(16) ?: "—"}…", fontSize = 11.sp, color = HColors.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val f = File(c.path)
                    if (f.exists()) {
                        val uris = arrayListOf(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
                        val json = File(f.path.removeSuffix(".mp4") + ".json")
                        if (json.exists()) uris += FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", json)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                    }
                }) { Text(T("Ulashish", "Поделиться", "Share")) }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(c.path).delete(); File(c.path.removeSuffix(".mp4") + ".json").delete(); app.db.deleteClip(c.id)
                        }
                        reload++
                    }
                }) { Text(T("O'chirish", "Удалить", "Delete")) }
            }
        }
    }
}

// ---------------- sozlamalar ----------------

@Composable
private fun SettingsSection(app: HamrohApp, onLanguage: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = app.prefs
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") tick
    fun changed() { tick++; DriveService.signal(ctx, DriveService.ACTION_REFRESH_SETTINGS) }

    Card(title = T("Til", "Язык", "Language")) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("uz" to "O'zbek", "ru" to "Русский", "en" to "English").forEach { (c, n) ->
                FilterChip(selected = p.language == c, onClick = { p.language = c; onLanguage() }, label = { Text(n) })
            }
        }
    }
    Card(title = T("Kamera", "Камера", "Camera")) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("road" to T("Yo'l", "Дорога", "Road"), "driver" to T("Charchoq", "Усталость", "Fatigue"), "off" to T("O'chiq", "Выкл.", "Off")).forEach { (c, n) ->
                FilterChip(selected = p.cameraMode == c, onClick = { p.cameraMode = c; changed() }, label = { Text(n) })
            }
        }
        Text(T("\"Charchoq\" rejimida old kamera ko'zni kuzatadi (tunda, trassada). Ikkala kamera bir vaqtda ishlamaydi.",
            "В режиме «Усталость» фронтальная камера следит за глазами. Две камеры одновременно не работают.",
            "In \"Fatigue\" mode the front camera watches your eyes. Both cameras cannot run at once."), color = HColors.TextDim, fontSize = 12.sp)
        Toggle(T("Videoregistrator", "Видеорегистратор", "Dashcam"), p.dashcamEnabled) { p.dashcamEnabled = it; changed() }
        Toggle(T("Lite rejim (kamerasiz)", "Lite-режим (без камеры)", "Lite mode (no camera)"), p.liteMode) { p.liteMode = it; changed() }
        Toggle(T("Kamera AI ovozsiz kuzatuv rejimi", "Камера ИИ в режиме наблюдения", "Camera AI shadow mode"), !p.shadowModeForcedOff) { p.shadowModeForcedOff = !it; changed() }
        var h by remember { mutableStateOf(p.cameraHeightM.toString()) }
        OutlinedTextField(h, { h = it; it.toFloatOrNull()?.takeIf { v -> v in 0.5f..3.5f }?.let { v -> p.cameraHeightM = v } },
            label = { Text(T("Telefon balandligi yerdan, m", "Высота телефона от земли, м", "Phone height above ground, m")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    Card(title = T("Ishga tushish", "Запуск", "Start-up")) {
        Toggle(T("Avtomatik yoqish", "Автозапуск", "Auto-start"), p.autoStart) { p.autoStart = it; changed() }
        Toggle(T("Navigator ustida oyna", "Окно поверх навигатора", "Window over navigator"), p.overlayEnabled) { p.overlayEnabled = it; changed() }
        Text(T("Mashina: ", "Машина: ", "Car: ") + (p.carBluetoothName ?: "—"), color = HColors.TextDim)
        OutlinedButton(onClick = { p.onboardingDone = false; onLanguage() }, modifier = Modifier.fillMaxWidth()) {
            Text(T("Ruxsatlar va mashinani qayta sozlash", "Перенастроить разрешения и машину", "Re-run permissions and car setup"))
        }
    }
    Card(title = T("Maktab vaqtlari", "Часы занятий", "School hours")) {
        var sched by remember { mutableStateOf(p.schoolScheduleCsv) }
        OutlinedTextField(sched, { sched = it; p.schoolScheduleCsv = it }, label = { Text("07:30-09:00,11:30-13:30,16:30-18:00") }, modifier = Modifier.fillMaxWidth())
        Text(T("Keyingi safardan kuchga kiradi.", "Применится со следующей поездки.", "Applies from the next trip."), color = HColors.TextDim, fontSize = 12.sp)
    }
    Card(title = T("Server", "Сервер", "Server")) {
        var url by remember { mutableStateOf(p.serverUrl) }
        OutlinedTextField(url, { url = it; p.serverUrl = it.trim() }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Toggle(T("Treklarni yozish (sinov uchun)", "Запись треков (для тестов)", "Record tracks (testing)"), p.recordTracks) { p.recordTracks = it; changed() }
    }
    Card(title = T("Maxfiylik", "Конфиденциальность", "Privacy")) {
        var confirm by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf<String?>(null) }
        OutlinedButton(onClick = {
            if (!confirm) { confirm = true; return@OutlinedButton }
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { if (app.api.configured && p.authToken != null) app.api.deleteMe() }
                    app.db.wipe()
                    File(ctx.filesDir, "dashcam").deleteRecursively()
                    File(ctx.filesDir, "tracks").deleteRecursively()
                }
                msg = T("Barcha ma'lumotlar o'chirildi", "Все данные удалены", "All data deleted"); confirm = false
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (confirm) T("Ishonchingiz komilmi? Yana bosing", "Уверены? Нажмите ещё раз", "Sure? Tap again") else T("Barcha ma'lumotlarimni o'chirish", "Удалить все мои данные", "Delete all my data"))
        }
        msg?.let { Text(it, color = HColors.Primary) }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().clickable { v = !v; onChange(v) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = v, onCheckedChange = { v = it; onChange(it) })
    }
}

// ---------------- ilova haqida + mualliflik ----------------

@Composable
private fun AboutSection(app: HamrohApp) {
    Card(title = "Hamroh AI") {
        Text("${T("Versiya", "Версия", "Version")} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(T("Yo'lda xavf haqida ogohlantiruvchi oflayn yordamchi. Mashinani haydovchi boshqaradi.",
            "Офлайн-помощник, предупреждающий об опасностях. Управляет водитель.",
            "An offline assistant that warns about road hazards. The driver stays in control."), color = HColors.TextDim)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(T("Ma'lumotlar: © OpenStreetMap hissadorlari (ODbL). Modellar: MediaPipe (Apache 2.0).",
            "Данные: © участники OpenStreetMap (ODbL). Модели: MediaPipe (Apache 2.0).",
            "Data: © OpenStreetMap contributors (ODbL). Models: MediaPipe (Apache 2.0)."), fontSize = 12.sp, color = HColors.TextDim)
    }
    AuthorshipSection(app)
}
