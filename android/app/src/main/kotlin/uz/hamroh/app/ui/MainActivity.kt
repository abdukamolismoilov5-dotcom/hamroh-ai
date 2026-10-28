package uz.hamroh.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color(0xFF08080E))) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(HColors.Border))
                NavigationBar(
                    containerColor = Color(0xFF08080E),
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
                                selectedIconColor = Color.Black,
                                selectedTextColor = Color.White,
                                indicatorColor = Color.White,
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
