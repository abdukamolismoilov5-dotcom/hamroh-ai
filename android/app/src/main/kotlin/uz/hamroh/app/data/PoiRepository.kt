package uz.hamroh.app.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * FR-2.1: POI bazasi. Manbalar:
 *  1) OpenStreetMap (Overpass API) — atrofdagi 10 km, internet bo'lganda, 7 kunda yoki 7 km siljiganda yangilanadi;
 *  2) assets/poi_seed.csv (ixtiyoriy, tools/poi_import bilan tayyorlanadi) — birinchi ishga tushishda;
 *  3) server tasdiqlagan jamoaviy nuqtalar (SyncWorker).
 * Hammasi mahalliy SQLite'da saqlanadi va oflayn ishlaydi.
 */
class PoiRepository(private val context: Context, private val db: LocalDb, private val prefs: Prefs) {
    private val tag = "PoiRepo"
    private val radiusM = 10_000
    @Volatile private var fetching = false

    fun nearby(center: LatLon, radiusM: Double = 1_500.0): List<Poi> = db.poisNear(center, radiusM)

    fun needsRefresh(here: LatLon, nowMs: Long): Boolean {
        if (prefs.poiFetchedAt == 0L) return true
        val moved = Geo.distanceM(here, LatLon(prefs.poiCenterLat.toDouble(), prefs.poiCenterLon.toDouble()))
        return moved > 7_000 || nowMs - prefs.poiFetchedAt > 7L * 24 * 3600 * 1000
    }

    fun loadSeedIfEmpty() {
        if (db.poiCount() > 0) return
        val text = runCatching { context.assets.open("poi_seed.csv").bufferedReader().readText() }.getOrNull() ?: return
        val pois = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split(',')
            runCatching {
                Poi(f[0], PoiType.valueOf(f[1]), f[2].toDouble(), f[3].toDouble(), f.getOrNull(4)?.toIntOrNull(), null, f.getOrNull(5))
            }.getOrNull()
        }.toList()
        db.upsertPois(pois, "seed")
        Log.i(tag, "seed: ${pois.size}")
    }

    /** Overpass'dan yuklash. Tarmoq oqimida chaqiriladi. Natija: yuklangan POI soni yoki -1 (xato). */
    fun refreshFromOsm(here: LatLon): Int {
        if (fetching) return 0
        fetching = true
        try {
            val a = "(around:$radiusM,${here.lat},${here.lon})"
            val q = """
                [out:json][timeout:40];
                (
                  nwr["amenity"="school"]$a;
                  nwr["amenity"="kindergarten"]$a;
                  node["highway"="speed_camera"]$a;
                  node["highway"="crossing"]$a;
                  node["traffic_calming"]$a;
                  nwr["amenity"="fuel"]["fuel:cng"="yes"]$a;
                  nwr["amenity"="fuel"]["fuel:lpg"="yes"]$a;
                );
                out center tags;
            """.trimIndent()
            val body = "data=" + URLEncoder.encode(q, "UTF-8")
            var lastError: Exception? = null
            for (endpoint in ENDPOINTS) {
                try {
                    val json = post(endpoint, body)
                    val pois = parseOverpass(json)
                    db.upsertPois(pois, "osm")
                    prefs.poiCenterLat = here.lat.toFloat()
                    prefs.poiCenterLon = here.lon.toFloat()
                    prefs.poiFetchedAt = System.currentTimeMillis()
                    Log.i(tag, "OSM: ${pois.size} ta obyekt")
                    return pois.size
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Log.w(tag, "Overpass xatosi", lastError)
            return -1
        } finally {
            fetching = false
        }
    }

    private fun post(endpoint: String, body: String): String {
        val c = URL(endpoint).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.setRequestProperty("User-Agent", "HamrohAI/1.0 (Android)")
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

        /** Overpass JSON → POI ro'yxati. Test qilinadigan sof funksiya. */
        fun parseOverpass(json: String): List<Poi> {
            val arr = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            val out = ArrayList<Poi>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: continue
                val lat: Double
                val lon: Double
                if (e.has("lat")) { lat = e.getDouble("lat"); lon = e.getDouble("lon") } else {
                    val c = e.optJSONObject("center") ?: continue
                    lat = c.getDouble("lat"); lon = c.getDouble("lon")
                }
                val type = when {
                    tags.optString("amenity") == "school" -> PoiType.SCHOOL
                    tags.optString("amenity") == "kindergarten" -> PoiType.KINDERGARTEN
                    tags.optString("highway") == "speed_camera" -> PoiType.SPEED_CAMERA
                    tags.optString("amenity") == "fuel" -> PoiType.GAS_STATION
                    tags.has("traffic_calming") && tags.optString("traffic_calming") != "no" -> PoiType.SPEED_BUMP
                    tags.optString("highway") == "crossing" -> PoiType.CROSSWALK
                    else -> continue
                }
                val limit = tags.optString("maxspeed").takeWhile { it.isDigit() }.toIntOrNull()
                val dir = tags.optString("direction").toDoubleOrNull()
                val name = tags.optString("name:uz").ifEmpty { tags.optString("name") }.ifEmpty { null }
                out += Poi("osm-${e.optString("type")}-${e.getLong("id")}", type, lat, lon, limit, dir, name)
            }
            return out
        }
    }
}
