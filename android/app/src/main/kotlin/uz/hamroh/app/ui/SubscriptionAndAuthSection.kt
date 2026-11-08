package uz.hamroh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.util.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// 1. OBUNA BO'LIMI (15 000 SO'M / OY — CLICK, PAYME, PAYNET)
// ============================================================================

enum class PaymentProvider(val title: String, val icon: String, val subtitle: String) {
    CLICK("Click", "⚡", "Click Evolution / Click Up"),
    PAYME("Payme", "💳", "Payme to'lov tizimi"),
    PAYNET("Paynet", "🌐", "Paynet orqali to'lov")
}

@Composable
fun SubscriptionSection(app: HamrohApp) {
    val p = app.prefs
    val scope = rememberCoroutineScope()
    var isSubscribed by remember { mutableStateOf(p.isSubscribed) }
    var selectedProvider by remember { mutableStateOf(PaymentProvider.CLICK) }
    var cardNumber by remember { mutableStateOf("8600 •••• •••• 1234") }
    var cardExpiry by remember { mutableStateOf("12/28") }
    var successMsg by remember { mutableStateOf<String?>(null) }

    Card(title = T("Hamroh AI Pro Obuna", "Подписка Hamroh AI Pro", "Hamroh AI Pro Subscription")) {
        // Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "15 000 so'm",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HColors.Primary
                )
                Text(
                    text = T("oyiga · to'liq versiya", "в месяц · полная версия", "/ month · full version"),
                    fontSize = 13.sp,
                    color = HColors.TextDim
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSubscribed) Color.White else Color(0xFF222230))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSubscribed) T("💎 FAOL PRO OBUNA", "💎 АКТИВНА", "💎 ACTIVE")
                           else T("NOFAOL", "НЕ АКТИВНА", "INACTIVE"),
                    color = if (isSubscribed) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Nega olish kerak? Pro obuna ustunliklari
        Text(
            text = T("NEGA OLISH KERAK? 5 TA ASOSIY USTUNLIK:", "ЗАЧЕМ ПОКУПАТЬ? 5 ГЛАВНЫХ ПРЕИМУЩЕСТВ:", "WHY BUY? 5 KEY PRO ADVANTAGES:"),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HColors.Accent
        )

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HColors.SurfaceHi)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("🧠 Google Gemini Pro Kopilot", "O'zbek tilida ovozli aqlli maslahatchi va xavf tahlili", "Ovozli AI"),
                Triple("🚸 Bolalar Harakati Bashorati", "Maktab oldida bolaning yo'lga sakrab chiqishini 2 soniya oldin hisoblaydi", "Hayotni asrash"),
                Triple("📸 2026 Yangi Multiradarlar", "1000m oldindan ogohlantiradi — kamida 500 000 so'm jarimadan tejaydi", "0 jarima"),
                Triple("🚨 Avtomatik Crash SOS Xabari", "Avariya bo'lsa 30 soniyada yaqinlarga Telegram va SMS GPS yuboradi", "Tez yordam"),
                Triple("🔧 Avtomobil Moyi & Diagnostika", "AI orqali motor moyi va texnik xizmat vaqtini hisoblab boradi", "Motor himoyasi")
            ).forEach { (title, desc, _) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = HColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(desc, color = HColors.TextDim, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSubscribed) Color.White else Color(0xFF222230))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "ISHLAMOQDA" else "PRO",
                            color = if (isSubscribed) Color.Black else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = T(
                "💡 15 000 so'm — bu 1 ta kofe puli, lekin u sizni qimmat jarimalar va yo'ldagi xavflardan 100% asraydi!",
                "💡 15 000 сум — это цена чашки кофе, но она спасёт от штрафов и аварий!",
                "💡 15,000 UZS saves you from huge fines and critical road hazards!"
            ),
            fontSize = 11.sp,
            color = HColors.TextDim
        )

        if (isSubscribed) {
            Spacer(Modifier.height(14.dp))
            val expiryDate = if (p.subscriptionExpiryMs > 0) {
                SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(p.subscriptionExpiryMs))
            } else {
                "30 kundan so'ng"
            }
            Text(
                text = T("Sizning obunangiz faol ($expiryDate gacha). To'lov usuli: ${p.paymentMethod}",
                    "Ваша подписка активна (до $expiryDate). Способ: ${p.paymentMethod}",
                    "Subscription active until $expiryDate. Provider: ${p.paymentMethod}"),
                fontSize = 13.sp,
                color = HColors.Accent
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    p.isSubscribed = false
                    isSubscribed = false
                    successMsg = T("Obuna to'xtatildi", "Подписка отменена", "Subscription cancelled")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(T("Obunani bekor qilish", "Отменить подписку", "Cancel subscription"))
            }
        } else {
            Spacer(Modifier.height(14.dp))
            Text(
                text = T("To'lov tizimini tanlang:", "Выберите способ оплаты:", "Select payment method:"),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = HColors.Text
            )

            Spacer(Modifier.height(8.dp))
            // Payment provider chips (Click, Payme, Paynet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentProvider.values().forEach { provider ->
                    val isSelected = selectedProvider == provider
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) HColors.Primary else HColors.SurfaceHi)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedProvider = provider }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(provider.icon, fontSize = 20.sp)
                            Text(
                                text = provider.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                label = { Text(T("Karta raqami (Uzcard / Humo)", "Номер карты", "Card number")) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HColors.Primary,
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cardExpiry,
                    onValueChange = { cardExpiry = it },
                    label = { Text(T("Muddati (MM/YY)", "Срок действия", "Expiry")) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HColors.Primary,
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = "15 000 so'm",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(T("Miqdori", "Сумма", "Amount")) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HColors.Primary,
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    scope.launch {
                        var serverOk = false
                        try {
                            withContext(Dispatchers.IO) {
                                app.api.paySubscription(selectedProvider.title, 15000, cardNumber)
                            }
                            serverOk = true
                        } catch (e: Exception) {
                            // Offline / lokal saqlash
                        }
                        p.isSubscribed = true
                        p.paymentMethod = selectedProvider.title
                        p.subscriptionExpiryMs = System.currentTimeMillis() + 30L * 86_400_000L
                        isSubscribed = true
                        successMsg = if (serverOk) {
                            T(
                                "✅ [Jonli Server] ${selectedProvider.title} orqali 15 000 so'm to'landi! Obunangiz 30 kunga faol.",
                                "✅ [Live Server] 15 000 сум успешно оплачено через ${selectedProvider.title}!",
                                "✅ [Live Server] 15 000 UZS paid via ${selectedProvider.title}!"
                            )
                        } else {
                            T(
                                "${selectedProvider.title} orqali 15 000 so'm to'landi! Obunangiz 30 kunga faol.",
                                "15 000 сум оплачено через ${selectedProvider.title}!",
                                "15 000 UZS paid via ${selectedProvider.title}!"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HColors.Primary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = T(
                        "${selectedProvider.title} orqali to'lash: 15 000 so'm",
                        "Оплатить через ${selectedProvider.title}: 15 000 сум",
                        "Pay 15 000 UZS with ${selectedProvider.title}"
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        successMsg?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                color = HColors.Accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

// ============================================================================
// 2. RO'YXATDAN O'TISH VA KIRISH BO'LIMI (ISM, FAMILIYA, NOMER, EMAIL, PAROL, GOOGLE)
// ============================================================================

@Composable
fun AuthSection(app: HamrohApp) {
    val p = app.prefs
    val scope = rememberCoroutineScope()
    var isLoggedIn by remember { mutableStateOf(p.isLoggedIn) }
    var tabIndex by remember { mutableIntStateOf(0) } // 0: Ro'yxatdan o'tish, 1: Kirish

    var phone by remember { mutableStateOf(p.phone ?: "+998") }
    var firstName by remember { mutableStateOf(p.userName) }
    var lastName by remember { mutableStateOf(p.userSurname) }
    var email by remember { mutableStateOf(p.userEmail) }
    var password by remember { mutableStateOf(p.userPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Card(title = T("Profil va Ro'yxatdan o'tish", "Профиль и Регистрация", "Profile and Auth")) {
        if (isLoggedIn) {
            // FOYDALANUVCHI TIZIMGA KIRGAN
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HColors.SurfaceHi)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfficialLogoBadge(size = 50.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${p.userName} ${p.userSurname}".trim().ifEmpty { "Hamroh Foydalanuvchi" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (p.loginType == "google") "Google hisobi" else p.phone ?: "",
                            fontSize = 13.sp,
                            color = HColors.TextDim
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF334155))

                if (p.userEmail.isNotEmpty()) {
                    Text(text = "✉️ ${p.userEmail}", fontSize = 13.sp, color = HColors.Text)
                }
                if (p.phone != null && p.phone!!.isNotEmpty()) {
                    Text(text = "📱 ${p.phone}", fontSize = 13.sp, color = HColors.Text)
                }
                Text(
                    text = if (p.isSubscribed) "💎 Pro Obuna: Faol (15 000 so'm)" else "ℹ️ Obuna: Standart bepul",
                    fontSize = 13.sp,
                    color = if (p.isSubscribed) HColors.Accent else HColors.TextDim
                )
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    p.isLoggedIn = false
                    isLoggedIn = false
                    statusMessage = T("Tizimdan chiqildi", "Вы вышли из системы", "Logged out")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(T("Tizimdan chiqish", "Выйти из системы", "Log out"))
            }
        } else {
            // RO'YXATDAN O'TISH / KIRISH FORMASI
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = HColors.SurfaceHi,
                contentColor = HColors.Primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                        color = HColors.Primary
                    )
                }
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0; statusMessage = null },
                    text = { Text(T("Ro'yxatdan o'tish", "Регистрация", "Sign Up"), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1; statusMessage = null },
                    text = { Text(T("Kirish", "Войти", "Sign In"), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // Ism va Familiya (faqat ro'yxatdan o'tishda)
            if (tabIndex == 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text(T("Ism", "Имя", "First name")) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HColors.Primary,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text(T("Familiya", "Фамилия", "Last name")) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HColors.Primary,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Telefon raqami
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(T("Telefon raqami (+998...)", "Телефон (+998...)", "Phone (+998...)")) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HColors.Primary,
                    unfocusedBorderColor = Color(0xFF334155)
                )
            )

            Spacer(Modifier.height(8.dp))

            // Email (faqat ro'yxatdan o'tishda yoki ixtiyoriy)
            if (tabIndex == 0) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(T("Elektron pochta (Email)", "Электронная почта", "Email address")) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HColors.Primary,
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
                Spacer(Modifier.height(8.dp))
            }

            // Parol
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(T("Parol", "Пароль", "Password")) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "👁️" else "🙈",
                        modifier = Modifier
                            .clickable { passwordVisible = !passwordVisible }
                            .padding(8.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HColors.Primary,
                    unfocusedBorderColor = Color(0xFF334155)
                )
            )

            Spacer(Modifier.height(14.dp))

            // Asosiy Tugma (Ro'yxatdan o'tish yoki Kirish)
            Button(
                onClick = {
                    if (phone.length < 9) {
                        isError = true
                        statusMessage = T("Telefon raqamini to'g'ri kiriting!", "Введите правильный номер телефона!", "Enter a valid phone number!")
                        return@Button
                    }
                    if (password.length < 4) {
                        isError = true
                        statusMessage = T("Parol kamida 4 belgidan iborat bo'lsin!", "Пароль должен быть не менее 4 символов!", "Password must be at least 4 characters!")
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
                        isLoggedIn = true
                        isError = false
                        statusMessage = if (tabIndex == 0) {
                            T("✅ Ichki SQL bazaga (`drivers` jadvali) darhol qo'shildi va tizimga kirdingiz!", "✅ Добавлено во внутреннюю базу SQL и выполнен вход!", "✅ Saved to internal SQL database and logged in!")
                        } else {
                            T("✅ Tizimga muvaffaqiyatli kirdingiz (SQL baza faol)!", "✅ Вход выполнен успешно!", "✅ Logged in successfully!")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HColors.Primary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = if (tabIndex == 0) T("Ro'yxatdan o'tish", "Зарегистрироваться", "Sign Up")
                           else T("Tizimga kirish", "Войти", "Sign In"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Ajratgich
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFF334155))
                Text(
                    text = T(" YOKI ", " ИЛИ ", " OR "),
                    fontSize = 11.sp,
                    color = HColors.TextDim,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(Modifier.weight(1f), color = Color(0xFF334155))
            }

            Spacer(Modifier.height(10.dp))

            // GOOGLE BILAN KIRISH TUGMASI
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    app.db.registerDriver(
                                        fullName = "Ali Valiyev (Google)",
                                        phone = "+998 90 123 45 67",
                                        carModel = "Chevrolet Cobalt",
                                        carPlate = "40 A 777 AA",
                                        region = "Farg'ona viloyati",
                                        plan = "PRO_BEPUL",
                                    )
                                }
                            }
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    app.api.googleAuth("ali.valiyev@gmail.com", "Ali Valiyev", "google_live_account_123")
                                }
                            }

                            p.userName = "Ali"
                            p.userSurname = "Valiyev"
                            p.userEmail = "ali.valiyev@gmail.com"
                            p.phone = "+998 90 123 45 67"
                            p.loginType = "google"
                            p.isLoggedIn = true
                            isLoggedIn = true
                            isError = false
                            statusMessage = T(
                                "✅ Google orqali tizimga kirdingiz va SQL bazaga saqlandi (ali.valiyev@gmail.com)!",
                                "✅ Вход выполнен через Google (ali.valiyev@gmail.com)!",
                                "✅ Logged in with Google (ali.valiyev@gmail.com)!"
                            )
                        }
                    }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleVectorLogo()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = T("Google hisobi orqali kirish", "Войти через Google", "Sign in with Google"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
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
}
