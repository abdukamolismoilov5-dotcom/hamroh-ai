package uz.hamroh.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.drive.DriveBus
import uz.hamroh.app.drive.VoicePlayer
import uz.hamroh.app.util.T
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType

private fun poiColor(t: PoiType): Int = when (t) {
    PoiType.SCHOOL, PoiType.KINDERGARTEN -> AColor.rgb(242, 194, 48)
    PoiType.SPEED_CAMERA -> AColor.rgb(255, 46, 76)
    PoiType.CROSSWALK -> AColor.rgb(56, 189, 248)
    PoiType.SPEED_BUMP, PoiType.POTHOLE -> AColor.rgb(245, 158, 11)
    PoiType.GAS_STATION -> AColor.rgb(16, 185, 129)
}

private fun dot(color: Int, sizePx: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL; setColor(color); setStroke(3, AColor.WHITE); setSize(sizePx, sizePx)
}

private fun makeRadarIcon(ctx: Context, speed: Int?): Drawable {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Tashqi qizil doira
    paint.color = AColor.rgb(239, 68, 68)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
    // Ichki oq doira
    paint.color = AColor.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 7, paint)
    // Tezlik yozuvi (qora, qalin)
    paint.color = AColor.BLACK
    paint.textSize = 24f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val text = speed?.toString() ?: "📷"
    val yPos = (size / 2f - (paint.descent() + paint.ascent()) / 2)
    canvas.drawText(text, size / 2f, yPos, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

// Yandex xaritasi kafel manbasi
private val YANDEX_TILES = object : OnlineTileSourceBase(
    "YandexMap",
    0, 19, 256, ".png",
    arrayOf("https://core-renderer-tiles.maps.yandex.net/tiles?l=map&v=21.06.18-0&lang=ru_RU&scale=1&")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val z = MapTileIndex.getZoom(pMapTileIndex)
        return "${baseUrl}x=$x&y=$y&z=$z"
    }
}

@Composable
fun MapScreen(app: HamrohApp) {
    val ctx = LocalContext.current
    val ui by DriveBus.state.collectAsState()
    val scope = rememberCoroutineScope()
    val voice = remember { VoicePlayer(ctx) }
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var gas by remember { mutableStateOf<List<ServerApi.GasStation>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var selectedPoi by remember { mutableStateOf<Poi?>(null) }
    var mapSource by remember { mutableStateOf(app.prefs.mapTileSource) } // "yandex", "osm"
    var radarAlertSpoken by remember { mutableStateOf(false) }

    val center = remember {
        val lat = ui.lat ?: app.prefs.poiCenterLat.toDouble().takeIf { it != 0.0 } ?: 41.311
        val lon = ui.lon ?: app.prefs.poiCenterLon.toDouble().takeIf { it != 0.0 } ?: 69.279
        LatLon(lat, lon)
    }

    val map = remember {
        MapView(ctx).apply {
            setTileSource(if (mapSource == "yandex") YANDEX_TILES else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(center.lat, center.lon))
        }
    }

    DisposableEffect(mapSource) {
        map.setTileSource(if (mapSource == "yandex") YANDEX_TILES else TileSourceFactory.MAPNIK)
        onDispose { }
    }

    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach(); voice.release() }
    }

    LaunchedEffect(reload, ui.lat, ui.lon) {
        val c = map.mapCenter
        val here = LatLon(c.latitude, c.longitude)
        pois = withContext(Dispatchers.IO) {
            if (app.db.poiCount() == 0) app.poiRepo.loadSeedIfEmpty()
            app.db.poisNear(here, 8_000.0)
        }
        gas = withContext(Dispatchers.IO) { runCatching { app.api.gas(Geo.bbox(here, 8_000.0)) }.getOrDefault(emptyList()) }
        val radarCount = pois.count { it.type == PoiType.SPEED_CAMERA }
        status = T("${pois.size} ta obyekt (shu jumladan $radarCount ta radar)", "${pois.size} объектов ($radarCount радаров)", "${pois.size} objects ($radarCount radars)")
        
        map.overlays.clear()
        val gasById = gas.associateBy { it.id }
        for (p in pois) {
            val m = Marker(map)
            m.position = GeoPoint(p.lat, p.lon)
            if (p.type == PoiType.SPEED_CAMERA) {
                m.icon = makeRadarIcon(ctx, p.speedLimitKmh)
            } else {
                m.icon = dot(poiColor(p.type), if (p.type == PoiType.CROSSWALK) 22 else 32)
            }
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            val g = gasById[p.id]
            m.title = (p.name ?: poiName(p.type)) + (p.speedLimitKmh?.let { " · $it km/h" } ?: "") +
                (g?.let { "\n" + T("navbat", "очередь", "queue") + ": " + it.queue + (if (it.stale) " (?)" else "") } ?: "")
            
            m.setOnMarkerClickListener { clickedMarker, _ ->
                selectedPoi = p
                if (p.type == PoiType.SPEED_CAMERA) {
                    voice.play("speed_camera", AlertLevel.VOICE) {}
                }
                clickedMarker.showInfoWindow()
                true
            }
            map.overlays.add(m)
        }
        
        ui.lat?.let { lat ->
            val lon = ui.lon ?: 0.0
            val me = Marker(map)
            me.position = GeoPoint(lat, lon)
            me.icon = dot(AColor.rgb(2, 132, 199), 38)
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            me.title = T("Sizning mashinangiz", "Ваша машина", "Your vehicle")
            map.overlays.add(me)

            // Yaqin radarni tekshirish (500 metr ichida)
            val nearestCam = pois.filter { it.type == PoiType.SPEED_CAMERA }
                .minByOrNull { Geo.distanceM(LatLon(lat, lon), it.point) }
            if (nearestCam != null) {
                val d = Geo.distanceM(LatLon(lat, lon), nearestCam.point)
                if (d < 500.0 && !radarAlertSpoken) {
                    radarAlertSpoken = true
                    selectedPoi = nearestCam
                    voice.play("speed_camera", AlertLevel.VOICE) {}
                } else if (d > 700.0) {
                    radarAlertSpoken = false
                }
            }
        }
        map.invalidate()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())

        // 1. TOP BAR: LEGENDA VA RADAR STATISTIKASI
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HColors.Surface.copy(alpha = 0.92f))
                .border(1.dp, HColors.Border, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Legend(PoiType.SPEED_CAMERA)
            Legend(PoiType.SCHOOL)
            Legend(PoiType.GAS_STATION)
            Legend(PoiType.CROSSWALK)
            Legend(PoiType.SPEED_BUMP)
            Text(status, fontSize = 11.sp, color = HColors.TextDim, fontWeight = FontWeight.Medium)
        }

        // 2. TOP RIGHT: XARITA QATLAMI VA TUN/KUN
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val next = if (mapSource == "yandex") "osm" else "yandex"
                    mapSource = next
                    app.prefs.mapTileSource = next
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HColors.Surface,
                    contentColor = HColors.Text
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.border(1.dp, HColors.Border, RoundedCornerShape(10.dp))
            ) {
                Text(if (mapSource == "yandex") "🗺️ Yandex" else "🌐 OSM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 3. TANLANGAN RADAR / OBYEKT KARTASI (OVOZLI OGOHLANTIRISH BILAN)
        selectedPoi?.let { poi ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (poi.type == PoiType.SPEED_CAMERA) HColors.Danger else HColors.Accent)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (poi.type == PoiType.SPEED_CAMERA) "🚨 FOTORADAR" else poiName(poi.type).uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (poi.type == PoiType.SPEED_CAMERA) HColors.Danger else HColors.Accent
                            )
                            poi.speedLimitKmh?.let { spd ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("$spd km/s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            poi.name ?: poiName(poi.type),
                            fontSize = 12.sp,
                            color = HColors.Text,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        ui.lat?.let { lat ->
                            val dist = Geo.distanceM(LatLon(lat, ui.lon ?: 0.0), poi.point).toInt()
                            Text("Sizdan masofa: $dist metr", fontSize = 11.sp, color = HColors.TextDim)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (poi.type == PoiType.SPEED_CAMERA) {
                            Button(
                                onClick = { voice.play("speed_camera", AlertLevel.VOICE) {} },
                                colors = ButtonDefaults.buttonColors(containerColor = HColors.Danger),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🔊 Ovoz", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { selectedPoi = null },
                            colors = ButtonDefaults.buttonColors(containerColor = HColors.SurfaceHi),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("✕", fontSize = 12.sp, color = HColors.Text)
                        }
                    }
                }
            }
        }

        // 4. BOTTOM ACTION BUTTONS: YANGILASH VA ATROFNI YUKLASH
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { reload++ },
                colors = ButtonDefaults.buttonColors(containerColor = HColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(T("🔄 Yangilash", "🔄 Обновить", "🔄 Refresh"), color = if (HColors.isDark) Color.Black else Color.White, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    val c = map.mapCenter
                    status = T("Radarlar yangilanmoqda…", "Обновление радаров…", "Loading radars…")
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { app.poiRepo.refreshFromOsm(LatLon(c.latitude, c.longitude)) }
                        status = if (n >= 0) "Radarlar yangilandi: $n ta" else T("Oflayn baza faol", "Офлайн база активна", "Offline DB active")
                        reload++
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HColors.SurfaceHi),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, HColors.Border, RoundedCornerShape(12.dp))
            ) {
                Text(T("📡 Radarlarni yuklash", "📡 Загрузить радары", "📡 Load radars"), color = HColors.Text, fontWeight = FontWeight.Bold)
            }
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
