package uz.hamroh.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.drive.DriveService
import uz.hamroh.app.util.T
import uz.hamroh.core.safety.CrashSosMachine

/** FR-7.1/7.3: zarbadan keyingi to'liq ekranli hisob. Ilova o'zi qo'ng'iroq qilmaydi — faqat tugmalar. */
class SosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            HamrohTheme {
                val ui by DriveBus.state.collectAsState()
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF5A0E0A)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(T("Kuchli zarba sezildi", "Обнаружен сильный удар", "Strong impact detected"), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    val counting = ui.sosState == CrashSosMachine.State.COUNTDOWN
                    Text(
                        if (counting) "${ui.sosRemainingS}" else "✓",
                        fontSize = 120.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (counting) T("Javob bo'lmasa, yaqinlaringizga joylashuvingiz yuboriladi", "Если не ответите, близким уйдёт ваша геопозиция", "If you do not respond, your location goes to your family")
                        else T("Holat yangilandi", "Статус обновлён", "Status updated"),
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { DriveService.signal(this@SosActivity, DriveService.ACTION_SOS_CANCEL); finish() },
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Text(T("MEN YAXSHIMAN", "Я В ПОРЯДКЕ", "I'M OK"), fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }) { Text("112", fontSize = 22.sp) }
                        OutlinedButton(onClick = { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:103"))) }) { Text("103", fontSize = 22.sp) }
                    }
                    if (!counting) OutlinedButton(onClick = { finish() }) { Text(T("Yopish", "Закрыть", "Close")) }
                }
            }
        }
    }
}
