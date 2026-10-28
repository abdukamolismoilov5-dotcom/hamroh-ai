package uz.hamroh.app.ui

import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.util.T
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType

private fun poiColor(t: PoiType): Int = when (t) {
    PoiType.SCHOOL, PoiType.KINDERGARTEN -> AColor.rgb(242, 194, 48)
    PoiType.SPEED_CAMERA -> AColor.rgb(255, 99, 88)
    PoiType.CROSSWALK -> AColor.rgb(120, 170, 255)
    PoiType.SPEED_BUMP, PoiType.POTHOLE -> AColor.rgb(255, 150, 60)
    PoiType.GAS_STATION -> AColor.rgb(61, 220, 151)
}

private fun dot(color: Int, sizePx: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL; setColor(color); setStroke(3, AColor.WHITE); setSize(sizePx, sizePx)
}

@Composable
fun MapScreen(app: HamrohApp) {
    val ctx = LocalContext.current
    val ui by DriveBus.state.collectAsState()
    val scope = rememberCoroutineScope()
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var gas by remember { mutableStateOf<List<ServerApi.GasStation>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val center = remember {
        val lat = ui.lat ?: app.prefs.poiCenterLat.toDouble().takeIf { it != 0.0 } ?: 41.311
        val lon = ui.lon ?: app.prefs.poiCenterLon.toDouble().takeIf { it != 0.0 } ?: 69.279
        LatLon(lat, lon)
    }
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(center.lat, center.lon))
        }
    }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(reload) {
        val c = map.mapCenter
        val here = LatLon(c.latitude, c.longitude)
        pois = withContext(Dispatchers.IO) { app.db.poisNear(here, 4_000.0) }
        gas = withContext(Dispatchers.IO) { runCatching { app.api.gas(Geo.bbox(here, 6_000.0)) }.getOrDefault(emptyList()) }
        status = T("${pois.size} ta obyekt", "${pois.size} объектов", "${pois.size} objects")
        map.overlays.clear()
        val gasById = gas.associateBy { it.id }
        for (p in pois) {
            val m = Marker(map)
            m.position = GeoPoint(p.lat, p.lon)
            m.icon = dot(poiColor(p.type), if (p.type == PoiType.CROSSWALK) 22 else 34)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            val g = gasById[p.id]
            m.title = (p.name ?: poiName(p.type)) + (p.speedLimitKmh?.let { " · $it km/h" } ?: "") +
                (g?.let { "\n" + T("navbat", "очередь", "queue") + ": " + it.queue + (if (it.stale) " (?)" else "") } ?: "")
            map.overlays.add(m)
        }
        ui.lat?.let { lat ->
            val me = Marker(map)
            me.position = GeoPoint(lat, ui.lon ?: 0.0)
            me.icon = dot(AColor.rgb(30, 144, 255), 40)
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            me.title = T("Siz", "Вы", "You")
            map.overlays.add(me)
        }
        map.invalidate()
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xEE101016), RoundedCornerShape(12.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(PoiType.SCHOOL); Legend(PoiType.SPEED_CAMERA); Legend(PoiType.POTHOLE); Legend(PoiType.GAS_STATION); Legend(PoiType.CROSSWALK)
            Text(status, fontSize = 11.sp, color = HColors.TextDim)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { reload++ }) { Text(T("Yangilash", "Обновить", "Refresh")) }
            Button(onClick = {
                val c = map.mapCenter
                status = T("OSM'dan yuklanmoqda…", "Загрузка из OSM…", "Loading from OSM…")
                scope.launch {
                    val n = withContext(Dispatchers.IO) { app.poiRepo.refreshFromOsm(LatLon(c.latitude, c.longitude)) }
                    status = if (n >= 0) "OSM: $n" else T("Yuklanmadi (internet?)", "Не загружено (интернет?)", "Failed (internet?)")
                    reload++
                }
            }) { Text(T("Hududni yuklash", "Загрузить район", "Load area")) }
        }
    }
}

@Composable
private fun Legend(t: PoiType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(Color(poiColor(t)), CircleShape))
        Text(poiName(t), fontSize = 12.sp)
    }
}

fun poiName(t: PoiType) = when (t) {
    PoiType.SCHOOL -> T("Maktab", "Школа", "School")
    PoiType.KINDERGARTEN -> T("Bog'cha", "Детсад", "Kindergarten")
    PoiType.SPEED_CAMERA -> T("Radar", "Радар", "Camera")
    PoiType.CROSSWALK -> T("O'tish joyi", "Переход", "Crossing")
    PoiType.SPEED_BUMP -> T("Notekislik", "Лежачий", "Bump")
    PoiType.POTHOLE -> T("Chuqur", "Яма", "Pothole")
    PoiType.GAS_STATION -> T("Metan", "Метан", "CNG")
}
