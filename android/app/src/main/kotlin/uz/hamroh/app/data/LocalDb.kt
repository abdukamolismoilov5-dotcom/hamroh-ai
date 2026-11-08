package uz.hamroh.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import uz.hamroh.core.care.WeekStats
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mahalliy SQLite bazasi (TZ 5-bo'lim jadvallari + Ichki Haydovchilar SQL Registri). Barcha metodlar 100% oflayn ishlaydi. */
class LocalDb(context: Context) : SQLiteOpenHelper(context, "hamroh.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS poi(id TEXT PRIMARY KEY, type TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, " +
            "speed_limit INTEGER, direction REAL, name TEXT, source TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS poi_geo ON poi(lat, lon)")
        db.execSQL("CREATE TABLE IF NOT EXISTS crowd_queue(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, lat REAL NOT NULL, " +
            "lon REAL NOT NULL, severity REAL NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS trip(id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER, " +
            "distance_m REAL NOT NULL DEFAULT 0, max_speed REAL NOT NULL DEFAULT 0, night_m REAL NOT NULL DEFAULT 0, " +
            "speeding INTEGER NOT NULL DEFAULT 0, hard_brakes INTEGER NOT NULL DEFAULT 0, fatigue INTEGER NOT NULL DEFAULT 0, " +
            "distraction INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS alert_log(id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER, type TEXT NOT NULL, " +
            "level TEXT NOT NULL, confidence REAL, lat REAL, lon REAL, ts INTEGER NOT NULL, shadow INTEGER NOT NULL DEFAULT 0, " +
            "feedback INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS clip(id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL, sha256 TEXT, " +
            "started_at INTEGER NOT NULL, reason TEXT NOT NULL, protected INTEGER NOT NULL DEFAULT 0)")
        ensureDriversTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureDriversTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            ensureDriversTable(db)
        }
    }

    private fun ensureDriversTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS drivers(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "full_name TEXT NOT NULL, " +
                "phone TEXT NOT NULL UNIQUE, " +
                "car_model TEXT NOT NULL, " +
                "car_plate TEXT NOT NULL, " +
                "region TEXT NOT NULL, " +
                "safety_score INTEGER NOT NULL DEFAULT 100, " +
                "plan TEXT NOT NULL DEFAULT 'PRO_BEPUL', " +
                "created_at TEXT NOT NULL)"
        )
        val count = db.rawQuery("SELECT COUNT(*) FROM drivers", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        if (count == 0) {
            val seeds = listOf(
                arrayOf("Abdukamol Bozorov (Muallif)", "+998 90 123 45 67", "Chevrolet Cobalt", "40 A 777 AA", "Farg'ona viloyati", 99, "FOUNDER_VIP", "2026-11-01 09:00"),
                arrayOf("Jasurbek Karimov", "+998 91 654 32 10", "Chevrolet Gentra", "40 B 555 BB", "Farg'ona sh.", 94, "PRO_OBUNA", "2026-11-02 11:20"),
                arrayOf("Sardorbek Aliyev", "+998 93 777 88 99", "Chevrolet Onix", "01 M 888 MM", "Toshkent sh.", 97, "PRO_OBUNA", "2026-11-03 14:45"),
                arrayOf("Otabek Rustamov", "+998 94 321 00 11", "Chevrolet Damas", "50 K 112 KK", "Namangan viloyati", 86, "STANDART", "2026-11-04 08:15"),
                arrayOf("Ulug'bek Normatov", "+998 99 444 55 66", "Chevrolet Tracker-2", "60 Z 999 ZZ", "Andijon viloyati", 96, "PRO_OBUNA", "2026-11-05 17:30"),
                arrayOf("Dilshod Yo'ldoshev", "+998 97 222 33 44", "Chevrolet Nexia-3", "30 X 345 XX", "Samarqand viloyati", 91, "STANDART", "2026-11-06 19:10"),
            )
            for (s in seeds) {
                db.insertWithOnConflict("drivers", null, ContentValues().apply {
                    put("full_name", s[0] as String)
                    put("phone", s[1] as String)
                    put("car_model", s[2] as String)
                    put("car_plate", s[3] as String)
                    put("region", s[4] as String)
                    put("safety_score", s[5] as Int)
                    put("plan", s[6] as String)
                    put("created_at", s[7] as String)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    // ---------- HAYDOVCHILAR (SQL DRIVERS TABLE — 100% OFLAYN) ----------
    data class DriverRow(
        val id: Long,
        val fullName: String,
        val phone: String,
        val carModel: String,
        val carPlate: String,
        val region: String,
        val safetyScore: Int,
        val plan: String,
        val createdAt: String,
    )

    fun registerDriver(
        fullName: String,
        phone: String,
        carModel: String = "Chevrolet Cobalt",
        carPlate: String = "40 A 777 AA",
        region: String = "Farg'ona viloyati",
        plan: String = "PRO_BEPUL",
    ): Long {
        val cleanName = fullName.trim().ifBlank { "Yangi Haydovchi" }
        val cleanPhone = phone.trim().ifBlank { "+998 90 000 00 00" }
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val db = writableDatabase
        val existingId = db.rawQuery("SELECT id FROM drivers WHERE phone=?", arrayOf(cleanPhone)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
        return if (existingId != null) {
            db.update("drivers", ContentValues().apply {
                put("full_name", cleanName)
                put("car_model", carModel)
                put("car_plate", carPlate)
                put("region", region)
                put("created_at", nowStr)
            }, "id=?", arrayOf(existingId.toString()))
            existingId
        } else {
            db.insertWithOnConflict("drivers", null, ContentValues().apply {
                put("full_name", cleanName)
                put("phone", cleanPhone)
                put("car_model", carModel)
                put("car_plate", carPlate)
                put("region", region)
                put("safety_score", 100)
                put("plan", plan)
                put("created_at", nowStr)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun allDrivers(): List<DriverRow> {
        val out = ArrayList<DriverRow>()
        readableDatabase.rawQuery(
            "SELECT id,full_name,phone,car_model,car_plate,region,safety_score,plan,created_at FROM drivers ORDER BY id DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += DriverRow(
                    id = c.getLong(0),
                    fullName = c.getString(1),
                    phone = c.getString(2),
                    carModel = c.getString(3),
                    carPlate = c.getString(4),
                    region = c.getString(5),
                    safetyScore = c.getInt(6),
                    plan = c.getString(7),
                    createdAt = c.getString(8),
                )
            }
        }
        return out
    }

    fun totalAlertCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM alert_log", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    // ---------- POI ----------
    fun upsertPois(pois: List<Poi>, source: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (p in pois) {
                db.insertWithOnConflict("poi", null, ContentValues().apply {
                    put("id", p.id); put("type", p.type.name); put("lat", p.lat); put("lon", p.lon)
                    p.speedLimitKmh?.let { put("speed_limit", it) }
                    p.directionDeg?.let { put("direction", it) }
                    p.name?.let { put("name", it) }
                    put("source", source); put("updated_at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun poisNear(center: LatLon, radiusM: Double): List<Poi> {
        val b = Geo.bbox(center, radiusM)
        val out = ArrayList<Poi>()
        readableDatabase.rawQuery(
            "SELECT id,type,lat,lon,speed_limit,direction,name FROM poi WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
            arrayOf(b[0].toString(), b[2].toString(), b[1].toString(), b[3].toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val type = runCatching { PoiType.valueOf(c.getString(1)) }.getOrNull() ?: continue
                out += Poi(
                    c.getString(0), type, c.getDouble(2), c.getDouble(3),
                    if (c.isNull(4)) null else c.getInt(4),
                    if (c.isNull(5)) null else c.getDouble(5),
                    if (c.isNull(6)) null else c.getString(6),
                )
            }
        }
        return out
    }

    fun poiCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM poi", null).use { it.moveToFirst(); it.getInt(0) }

    // ---------- jamoaviy navbat ----------
    fun queueCrowd(type: String, lat: Double, lon: Double, severity: Double, tsSec: Long) {
        writableDatabase.insert("crowd_queue", null, ContentValues().apply {
            put("type", type); put("lat", lat); put("lon", lon); put("severity", severity); put("ts", tsSec)
        })
    }

    data class QueuedEvent(val id: Long, val type: String, val lat: Double, val lon: Double, val severity: Double, val ts: Long)

    fun pendingCrowd(limit: Int = 500): List<QueuedEvent> {
        val out = ArrayList<QueuedEvent>()
        readableDatabase.rawQuery("SELECT id,type,lat,lon,severity,ts FROM crowd_queue ORDER BY id LIMIT $limit", null).use { c ->
            while (c.moveToNext()) out += QueuedEvent(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getLong(5))
        }
        return out
    }

    fun deleteCrowd(maxId: Long) { writableDatabase.delete("crowd_queue", "id<=?", arrayOf(maxId.toString())) }

    // ---------- safarlar ----------
    fun startTrip(nowMs: Long): Long = writableDatabase.insert("trip", null, ContentValues().apply { put("started_at", nowMs) })

    fun updateTrip(id: Long, distanceM: Double, maxSpeed: Double, nightM: Double, endedAt: Long?) {
        writableDatabase.update("trip", ContentValues().apply {
            put("distance_m", distanceM); put("max_speed", maxSpeed); put("night_m", nightM)
            if (endedAt != null) put("ended_at", endedAt)
        }, "id=?", arrayOf(id.toString()))
    }

    fun bumpTrip(id: Long, column: String) {
        require(column in setOf("speeding", "hard_brakes", "fatigue", "distraction"))
        writableDatabase.execSQL("UPDATE trip SET $column=$column+1 WHERE id=?", arrayOf(id))
    }

    fun weekStats(sinceMs: Long): WeekStats =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(distance_m),0),COALESCE(SUM(speeding),0),COALESCE(SUM(hard_brakes),0)," +
                "COALESCE(SUM(night_m),0),COALESCE(SUM(fatigue),0),COALESCE(SUM(distraction),0) FROM trip WHERE started_at>=?",
            arrayOf(sinceMs.toString()),
        ).use { c ->
            c.moveToFirst()
            WeekStats(c.getDouble(0) / 1000, c.getInt(1), c.getInt(2), c.getDouble(3) / 1000, c.getInt(4), c.getInt(5))
        }

    data class TripRow(val id: Long, val startedAt: Long, val endedAt: Long?, val distanceM: Double, val maxSpeed: Double)

    fun recentTrips(limit: Int = 20): List<TripRow> {
        val out = ArrayList<TripRow>()
        readableDatabase.rawQuery("SELECT id,started_at,ended_at,distance_m,max_speed FROM trip ORDER BY id DESC LIMIT $limit", null).use { c ->
            while (c.moveToNext()) out += TripRow(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getDouble(3), c.getDouble(4))
        }
        return out
    }

    // ---------- ogohlantirishlar jurnali ----------
    fun logAlert(tripId: Long?, type: String, level: String, confidence: Float?, lat: Double?, lon: Double?, ts: Long, shadow: Boolean): Long =
        writableDatabase.insert("alert_log", null, ContentValues().apply {
            tripId?.let { put("trip_id", it) }
            put("type", type); put("level", level)
            confidence?.let { put("confidence", it) }
            lat?.let { put("lat", it) }; lon?.let { put("lon", it) }
            put("ts", ts); put("shadow", if (shadow) 1 else 0)
        })

    data class AlertRow(val id: Long, val type: String, val level: String, val confidence: Float?, val ts: Long, val shadow: Boolean, val feedback: Int?)

    fun alertsForReview(limit: Int = 50): List<AlertRow> {
        val out = ArrayList<AlertRow>()
        readableDatabase.rawQuery(
            "SELECT id,type,level,confidence,ts,shadow,feedback FROM alert_log WHERE type IN " +
                "('PEDESTRIAN','PEDESTRIAN_CHILD','FORWARD_COLLISION','FATIGUE') ORDER BY id DESC LIMIT $limit", null,
        ).use { c ->
            while (c.moveToNext()) out += AlertRow(
                c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getFloat(3),
                c.getLong(4), c.getInt(5) == 1, if (c.isNull(6)) null else c.getInt(6),
            )
        }
        return out
    }

    fun setFeedback(alertId: Long, correct: Boolean) {
        writableDatabase.update("alert_log", ContentValues().apply { put("feedback", if (correct) 1 else 0) }, "id=?", arrayOf(alertId.toString()))
    }

    /** O'lchov: baholangan AI ogohlantirishlari ichida noto'g'rilari ulushi. */
    fun falseAlertRate(): Pair<Int, Int> = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(CASE WHEN feedback=0 THEN 1 ELSE 0 END),0), COUNT(feedback) FROM alert_log WHERE feedback IS NOT NULL", null,
    ).use { c -> c.moveToFirst(); c.getInt(0) to c.getInt(1) }

    // ---------- kliplar ----------
    fun addClip(path: String, sha256: String?, startedAt: Long, reason: String, protected: Boolean): Long =
        writableDatabase.insert("clip", null, ContentValues().apply {
            put("path", path); sha256?.let { put("sha256", it) }; put("started_at", startedAt); put("reason", reason)
            put("protected", if (protected) 1 else 0)
        })

    data class ClipRow(val id: Long, val path: String, val sha256: String?, val startedAt: Long, val reason: String)

    fun protectedClips(): List<ClipRow> {
        val out = ArrayList<ClipRow>()
        readableDatabase.rawQuery("SELECT id,path,sha256,started_at,reason FROM clip WHERE protected=1 ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) out += ClipRow(c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getString(2), c.getLong(3), c.getString(4))
        }
        return out
    }

    fun deleteClip(id: Long) { writableDatabase.delete("clip", "id=?", arrayOf(id.toString())) }

    /** FR-12 / maxfiylik: barcha mahalliy ma'lumotlarni o'chirish. */
    fun wipe() {
        val db = writableDatabase
        listOf("crowd_queue", "trip", "alert_log", "clip").forEach { db.delete(it, null, null) }
    }
}
