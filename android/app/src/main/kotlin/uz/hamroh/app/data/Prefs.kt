package uz.hamroh.app.data

import android.content.Context
import android.content.SharedPreferences
import uz.hamroh.app.BuildConfig
import uz.hamroh.app.util.Lang

/** Ilova sozlamalari (SharedPreferences). Maxfiy kalitlar bu yerda saqlanmaydi. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("hamroh", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(block: SharedPreferences.Editor.() -> Unit) = sp.edit().apply(block).apply()

    var consentAccepted: Boolean
        get() = sp.getBoolean("consent", false)
        set(v) = put { putBoolean("consent", v) }
    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding", false)
        set(v) = put { putBoolean("onboarding", v) }
    var language: String
        get() = str("lang", "uz")
        set(v) { put { putString("lang", v) }; Lang.current = v }
    /** FR-1.1: tanlangan mashina Bluetooth qurilmasi manzili. */
    var carBluetoothAddress: String?
        get() = sp.getString("bt_addr", null)
        set(v) = put { putString("bt_addr", v) }
    var carBluetoothName: String?
        get() = sp.getString("bt_name", null)
        set(v) = put { putString("bt_name", v) }
    var autoStart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) = put { putBoolean("autostart", v) }
    /** FR-12.4: Lite rejim (kamerasiz). */
    var liteMode: Boolean
        get() = sp.getBoolean("lite", false)
        set(v) = put { putBoolean("lite", v) }
    /** "road" — yo'l kamerasi (piyoda, oldingi mashina), "driver" — old kamera (charchoq), "off". */
    var cameraMode: String
        get() = str("cam_mode", "road")
        set(v) = put { putString("cam_mode", v) }
    var dashcamEnabled: Boolean
        get() = sp.getBoolean("dashcam", true)
        set(v) = put { putBoolean("dashcam", v) }
    var overlayEnabled: Boolean
        get() = sp.getBoolean("overlay", true)
        set(v) = put { putBoolean("overlay", v) }
    /** FR-4.9: kuzatuv rejimi boshlangan vaqt (epoch ms). */
    var shadowModeStartMs: Long
        get() = sp.getLong("shadow_start", 0L)
        set(v) = put { putLong("shadow_start", v) }
    var shadowModeForcedOff: Boolean
        get() = sp.getBoolean("shadow_off", true)
        set(v) = put { putBoolean("shadow_off", v) }
    var serverUrl: String
        get() = str("server", BuildConfig.SERVER_URL).ifEmpty { "http://10.18.212.172:8000" }
        set(v) = put { putString("server", v.trimEnd('/')) }
    /** "dark" (Tun) yoki "light" (Kun) */
    var themeMode: String
        get() = str("theme_mode", "dark")
        set(v) = put { putString("theme_mode", v) }
    /** "yandex" yoki "osm" */
    var mapTileSource: String
        get() = str("map_source", "yandex")
        set(v) = put { putString("map_source", v) }
    var authToken: String?
        get() = sp.getString("token", null)
        set(v) = put { putString("token", v) }
    var phone: String?
        get() = sp.getString("phone", null)
        set(v) = put { putString("phone", v) }
    /** Kamera o'rnatilgan balandlik, m (FR-4.3). */
    var cameraHeightM: Float
        get() = sp.getFloat("cam_h", 1.25f)
        set(v) = put { putFloat("cam_h", v) }
    var schoolScheduleCsv: String
        get() = str("school_sched", "07:30-09:00,11:30-13:30,16:30-18:00")
        set(v) = put { putString("school_sched", v) }
    var homeLat: Double?
        get() = if (sp.contains("home_lat")) sp.getFloat("home_lat", 0f).toDouble() else null
        set(v) = put { if (v == null) remove("home_lat") else putFloat("home_lat", v.toFloat()) }
    var homeLon: Double?
        get() = if (sp.contains("home_lon")) sp.getFloat("home_lon", 0f).toDouble() else null
        set(v) = put { if (v == null) remove("home_lon") else putFloat("home_lon", v.toFloat()) }
    var arrivedNotify: Boolean
        get() = sp.getBoolean("arrived", false)
        set(v) = put { putBoolean("arrived", v) }
    /** SOS SMS uchun telefonlar (vergul bilan). */
    var sosPhones: String
        get() = str("sos_phones", "")
        set(v) = put { putString("sos_phones", v) }
    // --- FR-9 avtomobil ---
    var odometerKm: Float
        get() = sp.getFloat("odo", 0f)
        set(v) = put { putFloat("odo", v) }
    var lastOilKm: Float
        get() = sp.getFloat("oil_km", 0f)
        set(v) = put { putFloat("oil_km", v) }
    var oilIntervalKm: Int
        get() = sp.getInt("oil_int", 7000)
        set(v) = put { putInt("oil_int", v) }
    var oilNotifiedAtKmLeft: Float
        get() = sp.getFloat("oil_notified", Float.NaN)
        set(v) = put { putFloat("oil_notified", v) }
    fun docDate(kind: String): Long = sp.getLong("doc_$kind", 0L)
    fun setDocDate(kind: String, epochDay: Long) = put { putLong("doc_$kind", epochDay) }
    fun docStage(kind: String): Int = sp.getInt("doc_stage_$kind", 0)
    fun setDocStage(kind: String, stage: Int) = put { putInt("doc_stage_$kind", stage) }
    // --- POI kesh ---
    var poiCenterLat: Float
        get() = sp.getFloat("poi_lat", 0f)
        set(v) = put { putFloat("poi_lat", v) }
    var poiCenterLon: Float
        get() = sp.getFloat("poi_lon", 0f)
        set(v) = put { putFloat("poi_lon", v) }
    var poiFetchedAt: Long
        get() = sp.getLong("poi_at", 0L)
        set(v) = put { putLong("poi_at", v) }
    var crowdSyncedAt: Long
        get() = sp.getLong("crowd_at", 0L)
        set(v) = put { putLong("crowd_at", v) }
    var recordTracks: Boolean
        get() = sp.getBoolean("rec_tracks", false)
        set(v) = put { putBoolean("rec_tracks", v) }
    var points: Int
        get() = sp.getInt("points", 0)
        set(v) = put { putInt("points", v) }

    // --- Foydalanuvchi profili va Ro'yxatdan o'tish ---
    var userName: String
        get() = str("user_name", "")
        set(v) = put { putString("user_name", v) }
    var userSurname: String
        get() = str("user_surname", "")
        set(v) = put { putString("user_surname", v) }
    var userEmail: String
        get() = str("user_email", "")
        set(v) = put { putString("user_email", v) }
    var userPassword: String
        get() = str("user_password", "")
        set(v) = put { putString("user_password", v) }
    var isLoggedIn: Boolean
        get() = sp.getBoolean("is_logged_in", false)
        set(v) = put { putBoolean("is_logged_in", v) }
    var authSkipped: Boolean
        get() = sp.getBoolean("auth_skipped", false)
        set(v) = put { putBoolean("auth_skipped", v) }
    var loginType: String
        get() = str("login_type", "phone")
        set(v) = put { putString("login_type", v) }

    // --- 15 000 so'm Obuna (Click, Payme, Paynet) ---
    var isSubscribed: Boolean
        get() = sp.getBoolean("is_subscribed", false)
        set(v) = put { putBoolean("is_subscribed", v) }
    var subscriptionPlan: String
        get() = str("sub_plan", "15 000 so'm / oy")
        set(v) = put { putString("sub_plan", v) }
    var paymentMethod: String
        get() = str("sub_payment_method", "")
        set(v) = put { putString("sub_payment_method", v) }
    var subscriptionExpiryMs: Long
        get() = sp.getLong("sub_expiry_ms", 0L)
        set(v) = put { putLong("sub_expiry_ms", v) }
}
