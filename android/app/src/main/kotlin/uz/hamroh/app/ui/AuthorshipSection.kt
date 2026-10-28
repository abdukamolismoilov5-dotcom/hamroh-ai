package uz.hamroh.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.util.T
import uz.hamroh.core.auth.AuthResult
import uz.hamroh.core.auth.Challenge
import java.util.concurrent.Executors

/**
 * FR-AU.2: "Mualliflikni tekshirish". Ilova so'rov yaratadi → egasi uni kompyuterida yopiq kalit bilan imzolaydi
 * (tools/sign_challenge.py) → imzo shu yerga QR yoki matn ko'rinishida kiritiladi → ilova ochiq kalit bilan tekshiradi.
 */
@Composable
fun AuthorshipSection(app: HamrohApp) {
    val ctx = LocalContext.current
    val auth = app.authorship
    var challenge by remember { mutableStateOf<Challenge?>(null) }
    var signature by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AuthResult?>(null) }
    var scanning by remember { mutableStateOf(false) }

    Card(title = T("Mualliflik", "Авторство", "Authorship")) {
        Text(T("Egasining kalit izi: ", "Отпечаток ключа владельца: ", "Owner key fingerprint: ") + (auth.ownerFingerprint ?: T("o'rnatilmagan", "не установлен", "not installed")),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(T("APK sertifikati: ", "Сертификат APK: ", "APK certificate: ") + auth.certSha256.take(32) + "…", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        when (auth.certMatches) {
            true -> Text(T("Rasmiy nusxa ✓", "Официальная сборка ✓", "Official build ✓"), color = HColors.Primary)
            false -> Text(T("DIQQAT: ilova o'zgartirilgan yoki rasmiy emas", "ВНИМАНИЕ: приложение изменено или неофициальное", "WARNING: modified or unofficial build"), color = HColors.Danger, fontWeight = FontWeight.Bold)
            null -> Text(T("Sinov (debug) yig'ilishi", "Тестовая (debug) сборка", "Test (debug) build"), color = HColors.TextDim)
        }
        if (!auth.ownerKeyInstalled) {
            Text(T("owner.pub joylanmagan — tekshirish faqat egasining kaliti o'rnatilgan relizda ishlaydi.",
                "owner.pub не установлен — проверка работает только в релизе с ключом владельца.",
                "owner.pub missing — verification works only in a release with the owner's key."), color = HColors.Accent, fontSize = 12.sp)
        }
        Button(onClick = { challenge = auth.newChallenge(); signature = ""; result = null }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(T("Mualliflikni tekshirish", "Проверить авторство", "Verify authorship"))
        }
    }

    challenge?.let { c ->
        Card(title = T("1. So'rov — egasiga yuboring", "1. Запрос — отправьте владельцу", "1. Challenge — send to the owner")) {
            val qr = remember(c) { qrBitmap(c.text, 640) }
            Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White))
            Text(c.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy(ctx, c.text) }) { Text(T("Nusxa olish", "Копировать", "Copy")) }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, c.text), null))
                }) { Text(T("Yuborish", "Отправить", "Share")) }
            }
            Text(T("10 daqiqa amal qiladi va bir marta ishlatiladi.", "Действует 10 минут, одноразовый.", "Valid for 10 minutes, single use."), color = HColors.TextDim, fontSize = 12.sp)
        }
        Card(title = T("2. Egasining imzosi", "2. Подпись владельца", "2. Owner's signature")) {
            OutlinedTextField(signature, { signature = it.trim() }, label = { Text(T("Imzo (base64)", "Подпись (base64)", "Signature (base64)")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                val running = DriveBus.state.value.running
                OutlinedButton(enabled = !running && ctx.granted(Manifest.permission.CAMERA), onClick = { scanning = !scanning }) {
                    Text(if (scanning) T("Skanerni yopish", "Закрыть сканер", "Close scanner") else T("QR skanerlash", "Сканировать QR", "Scan QR"))
                }
                Button(enabled = signature.isNotBlank(), onClick = { result = auth.verify(c, signature); scanning = false }) { Text(T("Tekshirish", "Проверить", "Verify")) }
            }
            if (DriveBus.state.value.running) Text(T("Skanerlash uchun haydashni to'xtating (kamera band).", "Остановите поездку для сканирования (камера занята).", "Stop driving to scan (camera busy)."), color = HColors.TextDim, fontSize = 12.sp)
            if (scanning) QrScanner { text -> signature = text.trim(); scanning = false; result = auth.verify(c, signature) }
        }
    }

    result?.let { r ->
        val (ok, text) = when (r) {
            is AuthResult.Verified -> true to T("EGASI TASDIQLANDI\nKalit izi: ${r.fingerprint}", "ВЛАДЕЛЕЦ ПОДТВЕРЖДЁН\nОтпечаток: ${r.fingerprint}", "OWNER VERIFIED\nFingerprint: ${r.fingerprint}")
            AuthResult.BadSignature -> false to T("Imzo noto'g'ri — bu egasining kaliti emas", "Подпись неверна — это не ключ владельца", "Bad signature — not the owner's key")
            AuthResult.Expired -> false to T("So'rov eskirgan, yangisini yarating", "Запрос устарел, создайте новый", "Challenge expired, create a new one")
            AuthResult.Reused, AuthResult.UnknownChallenge -> false to T("So'rov allaqachon ishlatilgan yoki noma'lum", "Запрос уже использован или неизвестен", "Challenge already used or unknown")
            AuthResult.Malformed -> false to T("Imzo formati noto'g'ri", "Неверный формат подписи", "Malformed signature")
            AuthResult.NoOwnerKey -> false to T("Ilovada egasining ochiq kaliti yo'q", "В приложении нет ключа владельца", "No owner key in this build")
        }
        Box(Modifier.fillMaxWidth().background(if (ok) HColors.Primary else HColors.Danger).padding(16.dp)) {
            Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = if (ok) FontFamily.Monospace else FontFamily.Default)
        }
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("hamroh", text))
}

fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** CameraX + ZXing: Y tekisligidan to'g'ridan-to'g'ri QR o'qish. */
@Composable
private fun QrScanner(onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(ctx) }
    DisposableEffect(Unit) {
        val exec = Executors.newSingleThreadExecutor()
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var done = false
        future.addListener({
            val p = future.get(); provider = p
            val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(exec) { img ->
                try {
                    if (done) return@setAnalyzer
                    val plane = img.planes[0]
                    val buf = plane.buffer
                    val data = ByteArray(buf.remaining()).also { buf.get(it) }
                    val src = PlanarYUVLuminanceSource(data, plane.rowStride, img.height, 0, 0, img.width, img.height, false)
                    val text = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text }.getOrNull()
                    reader.reset()
                    if (text != null) {
                        done = true
                        ContextCompat.getMainExecutor(ctx).execute { onResult(text) }
                    }
                } finally { img.close() }
            }
            runCatching { p.unbindAll(); p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, analysis) }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll(); exec.shutdown() }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) { AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize()) }
}
