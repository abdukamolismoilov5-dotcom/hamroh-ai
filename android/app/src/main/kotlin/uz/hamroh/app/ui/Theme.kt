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
import uz.hamroh.app.R

object HColors {
    val Bg = Color(0xFF06070A) // Deep Automotive Obsidian Black
    val Surface = Color(0xFF0F1117) // Carbon Slate Panel
    val SurfaceHi = Color(0xFF181B24) // Elevated Metallic Slate
    val Primary = Color(0xFFFFFFFF) // Crisp Pure White
    val Accent = Color(0xFF38BDF8) // Precision Sky Cyan
    val Warn = Color(0xFFF59E0B) // Amber Warning
    val Danger = Color(0xFFFF2E4C) // Crimson Alert
    val Success = Color(0xFF10B981) // Safe Status Indicator
    val Text = Color(0xFFFFFFFF) // 100% Pure White
    val TextDim = Color(0xFFCBD5E1) // High-contrast Silver-Gray (never dark!)
    val Border = Color(0xFF262B38) // Subtle Precision Hairline
}

@Composable
fun HamrohTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = HColors.Bg,
            contentColor = Color.White,
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                content()
            }
        }
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
