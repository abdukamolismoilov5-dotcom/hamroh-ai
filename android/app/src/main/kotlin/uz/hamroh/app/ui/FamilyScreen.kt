package uz.hamroh.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.util.T

@Composable
fun FamilyScreen(app: HamrohApp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by DriveBus.state.collectAsState()
    var contacts by remember { mutableStateOf<List<ServerApi.Contact>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var sosPhones by remember { mutableStateOf(app.prefs.sosPhones) }
    var arrived by remember { mutableStateOf(app.prefs.arrivedNotify) }
    var hasHome by remember { mutableStateOf(app.prefs.homeLat != null) }
    var smsGranted by remember { mutableStateOf(ctx.granted(Manifest.permission.SEND_SMS)) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsGranted = it }

    LaunchedEffect(reload) {
        if (!app.api.configured) return@LaunchedEffect
        contacts = withContext(Dispatchers.IO) { runCatching { app.api.contacts() }.getOrDefault(emptyList()) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(title = T("Favqulodda kontaktlar (Telegram)", "Экстренные контакты (Telegram)", "Emergency contacts (Telegram)")) {
            if (!app.api.configured) {
                Text(T("Server manzili sozlanmagan: Yana → Sozlamalar.", "Сервер не настроен: Ещё → Настройки.", "Server not configured: More → Settings."), color = HColors.Accent)
            } else {
                Text(T("Avariya bo'lsa yoki uyga yetib kelsangiz, shu odamlarga Telegram orqali xabar boradi.",
                    "При аварии или прибытии домой этим людям придёт сообщение в Telegram.",
                    "These people get a Telegram message after a crash or when you arrive home."), color = HColors.TextDim, fontSize = 13.sp)
                if (contacts.isEmpty()) Text(T("Hali kontakt yo'q", "Контактов пока нет", "No contacts yet"))
                contacts.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text((c.name ?: "Telegram") + if (c.role == "parent") " · " + T("ota-ona", "родитель", "parent") else "", Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { runCatching { app.api.deleteContact(c.id) } }; reload++ } }) {
                            Text(T("O'chirish", "Удалить", "Remove"))
                        }
                    }
                }
                Button(onClick = {
                    scope.launch {
                        val link = withContext(Dispatchers.IO) { runCatching { app.api.linkCode("family") }.getOrNull() }
                        if (link == null) message = T("Server bilan aloqa yo'q", "Нет связи с сервером", "No server connection")
                        else ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                            T("Meni Hamroh AI favqulodda kontaktingiz sifatida qo'shing: ", "Добавьте меня в экстренные контакты Hamroh AI: ", "Add me as your Hamroh AI emergency contact: ") + link), null))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(T("Yaqinni taklif qilish", "Пригласить близкого", "Invite a family member")) }
                OutlinedButton(onClick = { reload++ }, modifier = Modifier.fillMaxWidth()) { Text(T("Yangilash", "Обновить", "Refresh")) }
            }
        }

        Card(title = T("SMS zaxira (internetsiz)", "SMS-резерв (без интернета)", "SMS backup (offline)")) {
            OutlinedTextField(value = sosPhones, onValueChange = { sosPhones = it; app.prefs.sosPhones = it },
                label = { Text(T("Telefonlar, vergul bilan: +99890...", "Телефоны через запятую: +99890...", "Phones, comma-separated: +99890...")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            if (!smsGranted) OutlinedButton(onClick = { smsLauncher.launch(Manifest.permission.SEND_SMS) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(T("SMS yuborishga ruxsat", "Разрешить SMS", "Allow SMS"))
            }
            Text(T("Ilova o'zi qo'ng'iroq qilmaydi. Avariyadan keyin ekranda 112/103 tugmalari chiqadi.",
                "Приложение само не звонит. После аварии на экране появятся кнопки 112/103.",
                "The app never calls by itself. After a crash you get 112/103 buttons."), color = HColors.TextDim, fontSize = 12.sp)
        }

        Card(title = T("\"Yetib bordim\"", "«Я доехал»", "\"I've arrived\"")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(T("Uyga yetganda yaqinlarga xabar", "Сообщать близким о прибытии домой", "Notify family when I get home"), Modifier.weight(1f))
                Switch(checked = arrived, onCheckedChange = { arrived = it; app.prefs.arrivedNotify = it })
            }
            Text(if (hasHome) T("Uy manzili saqlangan", "Адрес дома сохранён", "Home saved") else T("Uy manzili belgilanmagan", "Дом не задан", "Home not set"), color = HColors.TextDim)
            OutlinedButton(enabled = ui.lat != null, onClick = {
                app.prefs.homeLat = ui.lat; app.prefs.homeLon = ui.lon; hasHome = true
            }, modifier = Modifier.fillMaxWidth()) { Text(T("Hozirgi joyni \"Uy\" deb saqlash", "Сохранить текущее место как «Дом»", "Save current place as Home")) }
        }

        Card(title = T("Avtorizatsiya", "Вход", "Sign in")) {
            PhoneLogin(app)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }, modifier = Modifier.weight(1f)) { Text("112") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:103"))) }, modifier = Modifier.weight(1f)) { Text("103") }
        }
        message?.let { Text(it, color = HColors.Accent) }
    }
}

/** FR-12.1: ixtiyoriy telefon raqami bilan kirish (SMS kod). */
@Composable
private fun PhoneLogin(app: HamrohApp) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf(app.prefs.phone ?: "+998") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    if (app.prefs.phone != null) {
        Text(T("Kirgansiz: ", "Вход выполнен: ", "Signed in: ") + app.prefs.phone)
        return
    }
    Text(T("Ixtiyoriy. Ilova ro'yxatdan o'tmasdan ham ishlaydi.", "Необязательно. Приложение работает и без входа.", "Optional. The app works without signing in."), color = HColors.TextDim, fontSize = 12.sp)
    OutlinedTextField(phone, { phone = it }, label = { Text(T("Telefon", "Телефон", "Phone")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
    if (sent) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(T("SMS kod", "Код из SMS", "SMS code")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        scope.launch {
            msg = withContext(Dispatchers.IO) {
                runCatching { if (!sent) app.api.requestOtp(phone.trim()) else app.api.verifyOtp(phone.trim(), code) }
                    .fold({ if (!sent) { sent = true; T("Kod yuborildi", "Код отправлен", "Code sent") } else T("Tayyor", "Готово", "Done") },
                        { it.message ?: "Error" })
            }
        }
    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (!sent) T("Kod olish", "Получить код", "Get code") else T("Tasdiqlash", "Подтвердить", "Confirm")) }
    msg?.let { Text(it, color = HColors.Accent) }
}
