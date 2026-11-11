package uz.hamroh.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.drive.VoicePlayer
import uz.hamroh.app.util.T
import uz.hamroh.core.model.AlertLevel

@Composable
fun AuthStartupScreen(
    app: HamrohApp,
    onProceed: () -> Unit
) {
    val ctx = LocalContext.current
    val p = app.prefs
    val scope = rememberCoroutineScope()
    val voice = remember { VoicePlayer(ctx) }
    DisposableEffect(Unit) {
        onDispose { voice.release() }
    }

    var tabIndex by remember { mutableIntStateOf(0) } // 0: Ro'yxatdan o'tish, 1: Kirish
    var currentLang by remember { mutableStateOf(p.language) }

    var phone by remember { mutableStateOf(p.phone ?: "+998 ") }
    var firstName by remember { mutableStateOf(p.userName) }
    var lastName by remember { mutableStateOf(p.userSurname) }
    var email by remember { mutableStateOf(p.userEmail) }
    var password by remember { mutableStateOf(p.userPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showGoogleDialog by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION")
    currentLang

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HColors.Accent,
        unfocusedBorderColor = Color(0xFF282E3D),
        focusedContainerColor = Color(0xFF141720),
        unfocusedContainerColor = Color(0xFF12151E),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = HColors.Accent,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = HColors.Accent,
        focusedLeadingIconColor = HColors.Accent,
        unfocusedLeadingIconColor = Color(0xFF94A3B8),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B0E17), Color(0xFF06070A), Color(0xFF040507))
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP SYSTEM BAR (Enterprise Status + Language Switcher)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111520))
                        .border(1.dp, Color(0xFF23293A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = HColors.Accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "ADAS CO-PILOT ENTERPRISE",
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111520))
                        .border(1.dp, Color(0xFF23293A), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("uz" to "UZ", "ru" to "RU", "en" to "EN").forEach { (code, label) ->
                        val active = currentLang == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) Color.White else Color.Transparent)
                                .clickable {
                                    p.language = code
                                    currentLang = code
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.Black else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 2. OFFICIAL LOGO HERO SECTION (Slayddagi rasmiy logotip)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x3338BDF8), Color.Transparent)
                        )
                    )
            ) {
                OfficialLogoBadge(size = 88.dp)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "HAMROH AI",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = T(
                    "Intellektual Yo'l Xavfsizligi va Avtopilot Tizimi",
                    "Интеллектуальная система дорожной безопасности",
                    "Intelligent Road Safety & Driver Assistance System"
                ),
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // 3. EXECUTIVE AUTHENTICATION PANEL
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D0F16))
                    .border(1.dp, Color(0xFF222736), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                // Modern Segmented Pill Switcher (Ro'yxatdan o'tish | Tizimga kirish)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151924))
                        .border(1.dp, Color(0xFF23293A), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    val tabs = listOf(
                        T("Ro'yxatdan o'tish", "Регистрация", "Create Account"),
                        T("Tizimga kirish", "Авторизация", "Sign In")
                    )
                    tabs.forEachIndexed { idx, title ->
                        val selected = tabIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable {
                                    tabIndex = idx
                                    statusMessage = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.Black else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (tabIndex == 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text(T("Ism", "Имя", "First name"), fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text(T("Familiya", "Фамилия", "Last name"), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(T("Telefon raqami", "Номер телефона", "Phone number"), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors
                )

                Spacer(Modifier.height(10.dp))

                if (tabIndex == 0) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(T("Elektron pochta (Email)", "Электронная почта", "Email address"), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = fieldColors
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(T("Maxfiy parol", "Пароль", "Password"), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    },
                    colors = fieldColors
                )

                Spacer(Modifier.height(16.dp))

                // Primary Submit Button
                Button(
                    onClick = {
                        if (phone.trim().length < 9) {
                            isError = true
                            statusMessage = T("Telefon raqamini to'g'ri kiriting", "Введите корректный номер телефона", "Enter a valid phone number")
                            return@Button
                        }
                        if (password.length < 4) {
                            isError = true
                            statusMessage = T("Parol kamida 4 belgidan iborat bo'lishi kerak", "Пароль должен быть не менее 4 символов", "Password must be at least 4 characters")
                            return@Button
                        }

                        scope.launch {
                            val fName = if (tabIndex == 0) firstName.ifEmpty { "Haydovchi" } else p.userName.ifEmpty { "Haydovchi" }
                            val lName = if (tabIndex == 0) lastName else p.userSurname
                            val fullName = "$fName $lName".trim()
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    app.db.registerDriver(
                                        fullName = fullName,
                                        phone = phone.trim(),
                                        carModel = "Chevrolet Cobalt",
                                        carPlate = "40 A 777 AA",
                                        region = "Farg'ona viloyati",
                                        plan = if (p.isSubscribed) "PRO_OBUNA" else "PRO_BEPUL",
                                    )
                                }
                            }
                            // Kompyuter yoqilgan bo'lsa fon rejimida sinxronlash (kutmasdan darhol kiradi!)
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    if (tabIndex == 0) {
                                        app.api.register(phone.trim(), fName, lName, email, password)
                                    } else {
                                        app.api.login(phone.trim(), password)
                                    }
                                }
                            }

                            p.phone = phone.trim()
                            p.userPassword = password
                            if (tabIndex == 0) {
                                p.userName = fName
                                p.userSurname = lName
                                p.userEmail = email
                            } else {
                                if (p.userName.isEmpty()) p.userName = fName
                            }
                            p.loginType = "phone"
                            p.isLoggedIn = true
                            isError = false
                            voice.play("drive_started", AlertLevel.VOICE) {}
                            onProceed()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (tabIndex == 0) T("Hisob yaratish va davom etish", "Создать аккаунт и продолжить", "Create Account & Continue")
                                   else T("Tizimga kirish", "Войти в систему", "Sign In"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFF222736))
                    Text(
                        text = T(" YOKI ", " ИЛИ ", " OR "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFF222736))
                }

                Spacer(Modifier.height(14.dp))

                // Realistic Google OAuth Button with Vector "G" Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151924))
                        .border(1.dp, Color(0xFF2A3144), RoundedCornerShape(12.dp))
                        .clickable {
                            showGoogleDialog = true
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleVectorLogo()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = T("Google hisobi orqali davom etish", "Продолжить через аккаунт Google", "Continue with Google"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                if (showGoogleDialog) {
                    GoogleAccountChooserDialog(
                        defaultName = if (p.userName.isNotBlank() && p.userName != "Haydovchi") p.userName else "Ismoilov Abdukamol",
                        defaultEmail = if (p.userEmail.isNotBlank()) p.userEmail else "abdukamolismoilov5@gmail.com",
                        onDismiss = { showGoogleDialog = false },
                        onSelect = { selectedName, selectedEmail ->
                            showGoogleDialog = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        app.db.registerDriver(
                                            fullName = "$selectedName (Google)",
                                            phone = p.phone?.ifBlank { "+998 90 123 45 67" } ?: "+998 90 123 45 67",
                                            carModel = "Chevrolet Cobalt",
                                            carPlate = "40 A 777 AA",
                                            region = "Farg'ona viloyati",
                                            plan = "PRO_BEPUL",
                                        )
                                    }
                                }
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        app.api.googleAuth(selectedEmail, selectedName, "google_" + selectedEmail.hashCode())
                                    }
                                }

                                p.userName = selectedName
                                p.userSurname = ""
                                p.userEmail = selectedEmail
                                if (p.phone.isNullOrBlank()) p.phone = "+998 90 123 45 67"
                                p.loginType = "google"
                                p.isLoggedIn = true
                                voice.play("drive_started", AlertLevel.VOICE) {}
                                onProceed()
                            }
                        }
                    )
                }

                statusMessage?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = msg,
                        color = if (isError) HColors.Danger else HColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4. BOTTOM UTILITY BAR (Voice Check + Quick Demo Access)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F121B))
                        .border(1.dp, Color(0xFF222736), RoundedCornerShape(12.dp))
                        .clickable {
                            voice.play("drive_started", AlertLevel.VOICE) {}
                            statusMessage = T(
                                "O'zbek tilidagi ovoz tizimi faol",
                                "Голосовая система активна",
                                "Uzbek voice engine active"
                            )
                            isError = false
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = HColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = T("Ovozni sinash", "Тест звука", "Voice Check"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F121B))
                        .border(1.dp, Color(0xFF2A3144), RoundedCornerShape(12.dp))
                        .clickable {
                            p.authSkipped = true
                            voice.play("drive_started", AlertLevel.VOICE) {}
                            onProceed()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = T("Demo rejimda kirish", "Демо-вход", "Demo Access"),
                        color = HColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = HColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun GoogleVectorLogo() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = size.width * 0.20f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Blue right arc + horizontal bar
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width * 0.94f, size.height * 0.50f),
            strokeWidth = stroke
        )
    }
}

@Composable
fun GoogleAccountChooserDialog(
    defaultName: String = "Ismoilov Abdukamol",
    defaultEmail: String = "abdukamolismoilov5@gmail.com",
    onDismiss: () -> Unit,
    onSelect: (name: String, email: String) -> Unit
) {
    var useCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customEmail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B202E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GoogleVectorLogo()
                Text(
                    text = T("Google hisobini tanlang", "Выберите аккаунт Google", "Choose a Google account"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = T(
                        "Hamroh AI tizimiga ulanish uchun Google hisobingiz:",
                        "Аккаунт Google для входа в Hamroh AI:",
                        "Google account to sign in to Hamroh AI:"
                    ),
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                if (!useCustom) {
                    // Account card 1: Ismoilov Abdukamol (abdukamolismoilov5@gmail.com)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF252C3D))
                            .border(1.dp, HColors.Accent, RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(defaultName, defaultEmail)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(HColors.Accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = defaultName.take(1).uppercase(),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = defaultName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = defaultEmail,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Button: Boshqa hisob kiritish
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141722))
                            .border(1.dp, Color(0xFF333B4F), RoundedCornerShape(12.dp))
                            .clickable { useCustom = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A3144)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = T("Boshqa Google hisob kiritish...", "Добавить другой аккаунт...", "Add another account..."),
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(T("Ism va familiya", "Имя и фамилия", "Full name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it },
                        label = { Text(T("Google Gmail pochta", "Эл. почта Google (Gmail)", "Google Gmail address")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val name = customName.trim().ifEmpty { "Google Foydalanuvchi" }
                            val email = customEmail.trim().ifEmpty { "user@gmail.com" }
                            onSelect(name, email)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = HColors.Accent, contentColor = Color.Black)
                    ) {
                        Text(T("Ushbu hisob bilan kirish", "Войти с этим аккаунтом", "Sign in with this account"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(T("Bekor qilish", "Отмена", "Cancel"), color = Color(0xFF94A3B8))
            }
        }
    )
}
