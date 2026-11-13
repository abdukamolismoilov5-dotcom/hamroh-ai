package uz.hamroh.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.auto.AutoStart
import uz.hamroh.app.util.Brand
import uz.hamroh.app.util.T

fun Context.granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/** Asosiy ruxsatlar (FR-12.3). Har biri alohida ma'noga ega; rad etilsa tegishli modul o'chadi. */
fun basePermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
}.toTypedArray()

@Composable
fun OnboardingFlow(app: HamrohApp, onLanguage: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(if (app.prefs.consentAccepted) 2 else 0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(HColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        // Rasmiy brend sarlavhasi + Slayddagi rasmiy logotip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OfficialLogoBadge(size = 56.dp)
            Column {
                Text(
                    text = "SAFEDRIVE AI",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = T(
                        "Tizim ruxsatlari va dastlabki sozlash",
                        "Системные разрешения и настройка",
                        "System permissions & initial setup"
                    ),
                    fontSize = 13.sp,
                    color = HColors.TextDim,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        when (step) {
            0 -> LanguageStep(app, onLanguage) { step = 1 }
            1 -> ConsentStep(app) { step = 2 }
            2 -> PermissionStep { step = 3 }
            3 -> BackgroundStep { step = 4 }
            else -> CarStep(app) {
                app.prefs.onboardingDone = true
                AutoStart.registerActivityTransitions(app)
                onDone()
            }
        }
    }
}

@Composable
private fun LanguageStep(app: HamrohApp, onLanguage: () -> Unit, next: () -> Unit) {
    Text(
        T("Tilni tanlang", "Выберите язык", "Choose a language"),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    listOf("uz" to "O'zbekcha", "ru" to "Русский", "en" to "English").forEach { (code, name) ->
        val isSel = app.prefs.language == code
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSel) HColors.SurfaceHi else HColors.Surface)
                .border(1.dp, if (isSel) HColors.Accent else HColors.Border, RoundedCornerShape(14.dp))
                .clickable { app.prefs.language = code; onLanguage() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSel,
                onClick = { app.prefs.language = code; onLanguage() },
                colors = RadioButtonDefaults.colors(selectedColor = HColors.Accent, unselectedColor = HColors.TextDim),
            )
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
    Button(
        onClick = next,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Text(T("Davom etish", "Продолжить", "Continue"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
    }
}

@Composable
private fun ConsentStep(app: HamrohApp, next: () -> Unit) {
    var agree by remember { mutableStateOf(true) }
    Card(title = T("Xavfsizlik va maxfiylik shartlari", "Безопасность и конфиденциальность", "Safety & Privacy Terms")) {
        Text(
            T(
                "SafeDrive AI yo'ldagi xavf haqida ovoz bilan ogohlantiruvchi intellektual yordamchidir. Avtomobilni haydovchi boshqaradi va barcha qarorlar haydovchida qoladi.\n\nVideo va yuz tasviri telefoningizdan tashqariga chiqmaydi — barcha AI tahlil qurilmaning o'zida (On-Device) bajariladi.",
                "SafeDrive AI — интеллектуальный помощник, предупреждающий об опасности голосом. Автомобилем управляет водитель.\n\nВидео и изображение лица не покидают телефон — весь ИИ-анализ выполняется на устройстве.",
                "SafeDrive AI is an intelligent assistant that warns about road hazards by voice. The driver stays in full control.\n\nVideo and face images never leave your phone — all AI processing runs locally on-device.",
            ),
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HColors.Surface)
            .border(1.dp, HColors.Border, RoundedCornerShape(12.dp))
            .clickable { agree = !agree }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = agree,
            onCheckedChange = { agree = it },
            colors = CheckboxDefaults.colors(checkedColor = HColors.Accent, checkmarkColor = Color.Black, uncheckedColor = HColors.TextDim),
        )
        Text(
            T("Shartlar bilan tanishdim va roziman", "Понимаю и согласен с условиями", "I understand and agree"),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    Button(
        enabled = agree,
        onClick = { app.prefs.consentAccepted = true; next() },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Text(T("Qabul qilaman va davom etish", "Принимаю и продолжить", "Accept & Continue"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
    }
}

@Composable
private fun PermissionStep(next: () -> Unit) {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result = it }

    Text(
        T("Kerakli tizim ruxsatlari", "Необходимые разрешения", "Required System Permissions"),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PermRow(
            title = T("GPS Joylashuv", "Геопозиция GPS", "GPS Location"),
            subtitle = T("Aniq tezlik, maktab hududlari va fotoradarlarni aniqlash uchun", "Для скорости, школьных зон и камер", "For speed, school zones, and cameras"),
            ok = ctx.granted(Manifest.permission.ACCESS_FINE_LOCATION),
        )
        PermRow(
            title = T("Kamera (AI Ko'rish)", "Камера (ИИ-зрение)", "Camera (AI Vision)"),
            subtitle = T("Yo'ldagi piyoda, avtomobil, charchoq va videoregistrator uchun", "Для распознавания пешеходов, авто и усталости", "For pedestrian, vehicle, fatigue detection & dashcam"),
            ok = ctx.granted(Manifest.permission.CAMERA),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            PermRow(
                title = T("Bildirishnomalar", "Уведомления", "Notifications"),
                subtitle = T("Fon rejimida xavfsizlik xabarlarini ko'rsatish uchun", "Для фоновых уведомлений безопасности", "For background safety alerts"),
                ok = ctx.granted(Manifest.permission.POST_NOTIFICATIONS),
            )
        }
        if (Build.VERSION.SDK_INT >= 29) {
            PermRow(
                title = T("Harakat datchigi", "Датчик движения", "Motion Recognition"),
                subtitle = T("Avtomobilda harakat boshlanganini avtomatik sezish uchun", "Для автозапуска при начале движения", "To detect when driving starts"),
                ok = ctx.granted(Manifest.permission.ACTIVITY_RECOGNITION),
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            PermRow(
                title = T("Bluetooth ulanish", "Bluetooth-подключение", "Bluetooth Connection"),
                subtitle = T("Mashina magnitolasiga ulanganda avtomatik yoqilish uchun", "Для запуска при подключении к магнитоле", "To auto-start when connected to car audio"),
                ok = ctx.granted(Manifest.permission.BLUETOOTH_CONNECT),
            )
        }
    }

    Text(
        T("Rad etilgan ruxsat faqat tegishli funksiyani o'chiradi.", "Отказ отключит только соответствующую функцию.", "Declining only disables that specific feature."),
        color = HColors.TextDim,
        fontSize = 13.sp,
    )

    Button(
        onClick = { launcher.launch(basePermissions()) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Text(T("Ruxsat berish", "Разрешить доступ", "Grant Permissions"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
    }

    OutlinedButton(
        onClick = next,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
    ) {
        Text(
            T("Davom etish", "Продолжить", "Continue"),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun PermRow(title: String, subtitle: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HColors.Surface)
            .border(1.dp, if (ok) HColors.Accent else HColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (ok) HColors.Accent else HColors.SurfaceHi)
                    .border(1.dp, if (ok) HColors.Accent else HColors.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (ok) "✓" else "•",
                    color = if (ok) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    color = HColors.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (ok) Color(0x2238BDF8) else HColors.SurfaceHi)
                .border(1.dp, if (ok) HColors.Accent else HColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (ok) T("Ruxsat bor", "Активно", "Granted") else T("Kerak", "Нужно", "Required"),
                color = if (ok) HColors.Accent else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BackgroundStep(next: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    @Suppress("UNUSED_EXPRESSION") tick

    Text(
        T("Fonda va navigator ustida ishlash", "Работа в фоне и поверх навигатора", "Background & Overlay Operation"),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )

    Card {
        Text(
            T(
                "SafeDrive AI mashinaga o'tirganingizda o'zi yonishi va Yandex/Google Navigator ustida ishlashi uchun quyidagi sozlamalarni yoqishingiz mumkin:",
                "Чтобы SafeDrive AI включался сам и работал поверх навигатора, включите следующие параметры:",
                "For SafeDrive AI to auto-start in your car and run over your navigation app, you can enable the following:"
            ),
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }

    if (Build.VERSION.SDK_INT >= 29) {
        val ok = ctx.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        PermRow(
            title = T("Joylashuv: \"Doim ruxsat\"", "Геопозиция: «Разрешить всегда»", "Location: \"Allow all the time\""),
            subtitle = T("Ekran o'chiq yoki navigatorda bo'lganda ham radar va maktablarni sezish uchun", "Для работы при выключенном экране", "To detect hazards while in background"),
            ok = ok,
        )
        if (!ok) {
            OutlinedButton(
                onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HColors.Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(T("Sozlash", "Настроить", "Set up"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    val overlayOk = Settings.canDrawOverlays(ctx)
    PermRow(
        title = T("Boshqa ilovalar ustida ko'rsatish", "Поверх других приложений", "Display over other apps"),
        subtitle = T("Navigator ustida suzuvchi ogohlantirish oynasini ko'rsatish uchun", "Для всплывающего окна поверх навигатора", "For floating HUD over navigation apps"),
        ok = overlayOk,
    )
    if (!overlayOk) {
        OutlinedButton(
            onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HColors.Accent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text(T("Sozlash", "Настроить", "Set up"), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    val batteryOk = Brand.isIgnoringBatteryOptimizations(ctx)
    PermRow(
        title = T("Batareya cheklovisiz ishlash", "Без ограничений батареи", "Unrestricted battery usage"),
        subtitle = T("Tizim fonda xizmatni to'xtatib qo'ymasligi uchun", "Чтобы система не останавливала службу в фоне", "Prevent system from stopping background service"),
        ok = batteryOk,
    )
    if (!batteryOk) {
        OutlinedButton(
            onClick = { Brand.requestIgnoreBattery(ctx) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HColors.Accent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text(T("Sozlash", "Настроить", "Set up"), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    Card(title = Build.MANUFACTURER.uppercase()) {
        Text(
            text = Brand.hint(),
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        OutlinedButton(
            onClick = { Brand.openAutostartSettings(ctx) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text(
                T("Telefon sozlamalarini ochish", "Открыть настройки телефона", "Open phone settings"),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Button(
        onClick = next,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Text(T("Davom etish", "Продолжить", "Continue"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun CarStep(app: HamrohApp, done: () -> Unit) {
    val ctx = LocalContext.current
    val devices = remember {
        if (Build.VERSION.SDK_INT >= 31 && !ctx.granted(Manifest.permission.BLUETOOTH_CONNECT)) emptyList()
        else runCatching {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices
                ?.map { (it.name ?: it.address) to it.address }.orEmpty()
        }.getOrDefault(emptyList())
    }
    var selected by remember { mutableStateOf(app.prefs.carBluetoothAddress) }
    Text(
        T("Avtomobil Bluetooth qurilmasi", "Bluetooth вашего автомобиля", "Your Car's Bluetooth"),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        T(
            "Tanlasangiz, SafeDrive AI shu qurilmaga ulanganda avtomatik ishga tushadi. Bo'lmasa, GPS tezlik orqali o'zi aniqlanadi.",
            "Если выбрать, SafeDrive AI включится при подключении. Иначе — автоматически по скорости.",
            "If selected, SafeDrive AI starts automatically when connected. Otherwise it detects driving by speed."
        ),
        color = HColors.TextDim,
        fontSize = 14.sp,
    )
    if (devices.isEmpty()) {
        Card {
            Text(
                T("Juftlangan Bluetooth qurilmalar topilmadi (keyinroq sozlashingiz mumkin).", "Сопряжённых устройств пока нет.", "No paired Bluetooth devices found."),
                color = Color.White,
                fontSize = 14.sp,
            )
        }
    }
    devices.forEach { (name, addr) ->
        val isSel = selected == addr
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSel) HColors.SurfaceHi else HColors.Surface)
                .border(1.dp, if (isSel) HColors.Accent else HColors.Border, RoundedCornerShape(12.dp))
                .clickable { selected = addr }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSel,
                onClick = { selected = addr },
                colors = RadioButtonDefaults.colors(selectedColor = HColors.Accent, unselectedColor = HColors.TextDim),
            )
            Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Button(
        onClick = {
            app.prefs.carBluetoothAddress = selected
            app.prefs.carBluetoothName = devices.firstOrNull { it.second == selected }?.first
            done()
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Text(T("Bosh ekranga o'tish", "Перейти на главный экран", "Launch Dashboard"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
    }
}
