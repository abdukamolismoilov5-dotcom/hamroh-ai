package uz.hamroh.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.R

object AppTheme {
    var isDark by mutableStateOf(true)
}

object HColors {
    val isDark get() = AppTheme.isDark
    val Bg get() = if (isDark) Color(0xFF06070A) else Color(0xFFF8FAFC) // Deep Obsidian Black vs Crisp Pure Light
    val Surface get() = if (isDark) Color(0xFF0F1117) else Color(0xFFFFFFFF)
    val SurfaceHi get() = if (isDark) Color(0xFF181B24) else Color(0xFFF1F5F9)
    val Primary get() = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val Accent get() = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val Warn = Color(0xFFF59E0B)
    val Danger = Color(0xFFFF2E4C)
    val Success = Color(0xFF10B981)
    val Text get() = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val TextDim get() = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
    val Border get() = if (isDark) Color(0xFF262B38) else Color(0xFFCBD5E1)
}

@Composable
fun HamrohTheme(content: @Composable () -> Unit) {
    val isDark = AppTheme.isDark
    val scheme = if (isDark) {
        darkColorScheme(
            primary = HColors.Primary,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF1E222D),
            onPrimaryContainer = Color.White,
            secondary = HColors.Accent,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF1E293B),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFFE2E8F0),
            onTertiary = Color.Black,
            tertiaryContainer = Color(0xFF1E222D),
            onTertiaryContainer = Color.White,
            background = HColors.Bg,
            onBackground = Color.White,
            surface = HColors.Surface,
            onSurface = Color.White,
            surfaceVariant = HColors.SurfaceHi,
            onSurfaceVariant = HColors.TextDim,
            outline = Color(0xFF3B4254),
            outlineVariant = HColors.Border,
            error = HColors.Danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0284C7),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0F2FE),
            onPrimaryContainer = Color(0xFF0369A1),
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE2E8F0),
            onSecondaryContainer = Color(0xFF0F172A),
            tertiary = Color(0xFF475569),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF1F5F9),
            onTertiaryContainer = Color(0xFF0F172A),
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF64748B),
            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFCBD5E1),
            error = HColors.Danger,
            onError = Color.White,
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = HColors.Bg,
            contentColor = HColors.Text,
        ) {
            CompositionLocalProvider(LocalContentColor provides HColors.Text) {
                content()
            }
        }
    }
}

@Composable
fun DayNightThemeToggle(app: HamrohApp, modifier: Modifier = Modifier) {
    val isDark = AppTheme.isDark
    Button(
        onClick = {
            AppTheme.isDark = !AppTheme.isDark
            app.prefs.themeMode = if (AppTheme.isDark) "dark" else "light"
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF1E222D) else Color(0xFFE2E8F0),
            contentColor = if (isDark) Color.White else Color(0xFF0F172A)
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(if (isDark) "☀️ Kun" else "🌙 Tun", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfficialLogoBadge(size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0A0D14))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFFE2E8F0), Color(0xFF38BDF8))
                ),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.official_logo),
            contentDescription = "Official Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HColors.Surface)
            .border(1.dp, HColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(HColors.Accent, RoundedCornerShape(2.dp))
                )
                Text(
                    text = title.uppercase(),
                    color = HColors.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }
        }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}
