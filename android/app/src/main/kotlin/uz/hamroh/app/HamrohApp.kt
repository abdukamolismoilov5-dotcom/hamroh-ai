package uz.hamroh.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.osmdroid.config.Configuration
import uz.hamroh.app.auth.AuthorshipManager
import uz.hamroh.app.care.CareWorker
import uz.hamroh.app.data.LocalDb
import uz.hamroh.app.data.PoiRepository
import uz.hamroh.app.data.Prefs
import uz.hamroh.app.data.ServerApi
import uz.hamroh.app.data.SyncWorker
import uz.hamroh.app.util.Lang

/** Ilova obyektlari (oddiy qo'lda DI). */
class HamrohApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var db: LocalDb
        private set
    lateinit var poiRepo: PoiRepository
        private set
    lateinit var api: ServerApi
        private set
    lateinit var authorship: AuthorshipManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Lang.current = prefs.language
        db = LocalDb(this)
        poiRepo = PoiRepository(this, db, prefs)
        api = ServerApi(prefs)
        authorship = AuthorshipManager(this)
        if (prefs.shadowModeStartMs == 0L) prefs.shadowModeStartMs = System.currentTimeMillis()
        Configuration.getInstance().userAgentValue = "HamrohAI/${BuildConfig.VERSION_NAME}"
        Configuration.getInstance().osmdroidBasePath = cacheDir.resolve("osmdroid")
        Configuration.getInstance().osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
        createChannels()
        SyncWorker.schedule(this)
        CareWorker.schedule(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DRIVE, "Haydash / Поездка / Driving", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_SOS, "SOS", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Eslatmalar / Напоминания / Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_AUTOSTART, "Avto-start", NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val CH_DRIVE = "drive"
        const val CH_SOS = "sos"
        const val CH_REMIND = "remind"
        const val CH_AUTOSTART = "autostart"
    }
}
