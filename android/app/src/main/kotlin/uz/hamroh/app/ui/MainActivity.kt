package uz.hamroh.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.util.T

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as HamrohApp
        AppTheme.isDark = app.prefs.themeMode == "dark"
        setContent {
            HamrohTheme {
                var langTick by remember { mutableIntStateOf(0) }
                var authDone by remember { mutableStateOf(app.prefs.isLoggedIn || app.prefs.authSkipped) }
                var ready by remember(langTick, authDone) { mutableStateOf(app.prefs.consentAccepted && app.prefs.onboardingDone) }

                // Til almashganda yoki holat o'zgarganda butun UI qayta chiziladi
                androidx.compose.runtime.key(langTick, authDone) {
                    if (!authDone) {
                        AuthStartupScreen(app) {
                            authDone = true
                        }
                    } else if (!ready) {
                        OnboardingFlow(app, onLanguage = { langTick++ }) { ready = true }
                    } else {
                        MainScaffold(app, onLanguage = { langTick++ })
                    }
                }
            }
        }
    }
}

private data class Tab(val title: () -> String, val icon: ImageVector)

@Composable
private fun MainScaffold(app: HamrohApp, onLanguage: () -> Unit) {
    val tabs = listOf(
        Tab({ T("Haydash", "Поездка", "Drive") }, Icons.Filled.Speed),
        Tab({ T("Xarita", "Карта", "Map") }, Icons.Filled.Map),
        Tab({ T("Oila", "Семья", "Family") }, Icons.Filled.FamilyRestroom),
        Tab({ T("Mashina", "Машина", "Car") }, Icons.Filled.DirectionsCar),
        Tab({ T("Yana", "Ещё", "More") }, Icons.Filled.MoreHoriz),
    )
    var current by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = HColors.Bg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HColors.Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OfficialLogoBadge(size = 32.dp)
                    Column {
                        Text(
                            "SAFEDRIVE AI",
                            color = HColors.Text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "Avtonom ADAS & Radar",
                            color = HColors.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayNightThemeToggle(app)
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(HColors.Surface)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(HColors.Border))
                NavigationBar(
                    containerColor = HColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { i, t ->
                        val isSelected = current == i
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { current = i },
                            icon = { Icon(t.icon, contentDescription = t.title()) },
                            label = {
                                Text(
                                    text = t.title(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (HColors.isDark) Color.Black else Color.White,
                                selectedTextColor = HColors.Text,
                                indicatorColor = HColors.Accent,
                                unselectedIconColor = HColors.TextDim,
                                unselectedTextColor = HColors.TextDim
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(HColors.Bg).padding(pad)) {
            when (current) {
                0 -> DriveScreen(app)
                1 -> MapScreen(app)
                2 -> FamilyScreen(app)
                3 -> CarScreen(app)
                else -> MoreScreen(app, onLanguage)
            }
        }
    }
}
