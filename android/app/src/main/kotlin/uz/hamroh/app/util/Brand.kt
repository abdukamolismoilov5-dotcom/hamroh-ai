package uz.hamroh.app.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** FR-1.4: brendga xos batareya sozlamalari sahifasini ochish. */
object Brand {
    val name: String get() = Build.MANUFACTURER.lowercase()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** Tizimning "batareya tejashni o'chirish" so'rovi. */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBattery(context: Context) {
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        startSafe(context, i) || startSafe(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Brend sahifalari: avtostart / fon cheklovlari. Topilmasa — ilova ma'lumotlari sahifasi. */
    fun openAutostartSettings(context: Context) {
        val candidates = when {
            name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            )
            name.contains("tecno") || name.contains("infinix") || name.contains("itel") -> listOf(
                ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
                ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.ui.autostart.AutoStartActivity"),
            )
            name.contains("samsung") -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            )
            name.contains("honor") || name.contains("huawei") -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            name.contains("oppo") || name.contains("realme") -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            )
            name.contains("vivo") -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            )
            else -> emptyList()
        }
        for (c in candidates) if (startSafe(context, Intent().setComponent(c))) return
        startSafe(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    fun hint(): String = when {
        name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") ->
            T("1) \"Avtostart\" ni yoqing. 2) Batareya: \"Cheklovsiz\" ni tanlang.",
                "1) Включите «Автозапуск». 2) Батарея: выберите «Нет ограничений».",
                "1) Enable \"Autostart\". 2) Battery: choose \"No restrictions\".")
        name.contains("tecno") || name.contains("infinix") ->
            T("1) Phone Master → Avtostart: Hamroh'ni yoqing. 2) Fonda ishlashga ruxsat bering.",
                "1) Phone Master → Автозапуск: включите Hamroh. 2) Разрешите работу в фоне.",
                "1) Phone Master → Auto-start: enable Hamroh. 2) Allow background activity.")
        name.contains("samsung") ->
            T("Batareya → Fon cheklovlari → Hamroh'ni \"Uxlamaydigan ilovalar\" ga qo'shing.",
                "Батарея → Ограничения фона → добавьте Hamroh в «Приложения без спящего режима».",
                "Battery → Background limits → add Hamroh to \"Never sleeping apps\".")
        else -> T("Batareya tejashdan Hamroh'ni chiqaring.", "Исключите Hamroh из экономии батареи.", "Exclude Hamroh from battery optimization.")
    }

    private fun startSafe(context: Context, i: Intent): Boolean = try {
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }
}
