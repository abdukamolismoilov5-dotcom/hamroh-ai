package uz.hamroh.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.care.CareWorker
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.util.T
import uz.hamroh.core.care.CarCare
import uz.hamroh.core.care.DocKind
import java.time.LocalDate

@Composable
fun CarScreen(app: HamrohApp) {
    val p = app.prefs
    var odo by remember { mutableStateOf(p.odometerKm.toInt().toString()) }
    var oil by remember { mutableStateOf(p.lastOilKm.toInt().toString()) }
    var interval by remember { mutableStateOf(p.oilIntervalKm.toString()) }
    val left = CarCare.oilKmLeft(odo.toDoubleOrNull() ?: 0.0, oil.toDoubleOrNull() ?: 0.0, interval.toIntOrNull() ?: 7000)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(title = T("Moy almashtirish", "Замена масла", "Oil change")) {
            Text(
                if ((oil.toDoubleOrNull() ?: 0.0) <= 0) T("Oxirgi almashtirishni kiriting", "Укажите последнюю замену", "Enter the last change")
                else if (left > 0) T("${left.toInt()} km qoldi", "Осталось ${left.toInt()} км", "${left.toInt()} km left")
                else T("${(-left).toInt()} km o'tib ketdi!", "Просрочено на ${(-left).toInt()} км!", "Overdue by ${(-left).toInt()} km!"),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (left > 500) HColors.Primary else HColors.Accent,
            )
            NumField(T("Hozirgi probeg, km", "Текущий пробег, км", "Odometer, km"), odo) { odo = it; it.toFloatOrNull()?.let { v -> p.odometerKm = v } }
            NumField(T("Oxirgi almashtirish probegi, km", "Пробег при замене, км", "Odometer at last change, km"), oil) {
                oil = it; it.toFloatOrNull()?.let { v -> p.lastOilKm = v; p.oilNotifiedAtKmLeft = Float.NaN }
            }
            NumField(T("Oraliq, km", "Интервал, км", "Interval, km"), interval) { interval = it; it.toIntOrNull()?.let { v -> p.oilIntervalKm = v } }
            Text(T("Probeg har safardan keyin GPS bo'yicha o'zi oshadi.", "Пробег увеличивается по GPS после каждой поездки.", "Odometer grows by GPS after each trip."), color = HColors.TextDim, fontSize = 12.sp)
        }

        Card(title = T("Hujjatlar muddati", "Сроки документов", "Document deadlines")) {
            DocKind.entries.forEach { k -> DocRow(app, k) }
            Text(T("Eslatma 30, 7 va 1 kun oldin keladi.", "Напоминание за 30, 7 и 1 день.", "Reminders 30, 7 and 1 day before."), color = HColors.TextDim, fontSize = 12.sp)
        }

        Offers(app)
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() }) }, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DocRow(app: HamrohApp, k: DocKind) {
    val stored = app.prefs.docDate(k.name)
    var text by remember { mutableStateOf(if (stored == 0L) "" else LocalDate.ofEpochDay(stored).toString()) }
    val parsed = runCatching { LocalDate.parse(text) }.getOrNull()
    val days = parsed?.let { it.toEpochDay() - LocalDate.now().toEpochDay() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, {
            text = it
            runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> app.prefs.setDocDate(k.name, d.toEpochDay()); app.prefs.setDocStage(k.name, 0) }
        }, label = { Text(CareWorker.docTitle(k)) }, placeholder = { Text("2026-12-31") }, singleLine = true, modifier = Modifier.weight(1f))
        days?.let { Text(if (it >= 0) T("$it kun", "$it дн.", "$it d") else T("o'tgan", "просрочено", "expired"), color = if (it > 30) HColors.TextDim else HColors.Accent) }
    }
}

/** FR-9.3: yaqin hamkor takliflari — aniq "Reklama" belgisi bilan. */
@Composable
private fun Offers(app: HamrohApp) {
    val ctx = LocalContext.current
    val ui by DriveBus.state.collectAsState()
    var offers by remember { mutableStateOf<List<ServerApi.Offer>>(emptyList()) }
    val lat = ui.lat ?: app.prefs.poiCenterLat.toDouble()
    val lon = ui.lon ?: app.prefs.poiCenterLon.toDouble()
    LaunchedEffect(lat.toInt(), lon.toInt()) {
        if (!app.api.configured || lat == 0.0) return@LaunchedEffect
        offers = withContext(Dispatchers.IO) { runCatching { app.api.offers(lat, lon, "oil") }.getOrDefault(emptyList()) }
    }
    if (offers.isEmpty()) return
    Card(title = T("Reklama · yaqin servislar", "Реклама · сервисы рядом", "Ad · nearby services")) {
        offers.forEach { o ->
            Text(o.title, fontWeight = FontWeight.SemiBold)
            Text("${o.body} · ${o.distanceM} m", color = HColors.TextDim, fontSize = 13.sp)
            o.phone?.let { ph ->
                OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) }) { Text(ph) }
            }
        }
    }
}
