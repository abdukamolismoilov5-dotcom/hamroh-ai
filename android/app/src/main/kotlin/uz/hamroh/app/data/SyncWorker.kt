package uz.hamroh.app.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.hamroh.app.HamrohApp
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.geo.LatLon
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType
import java.util.concurrent.TimeUnit

/**
 * Fon sinxronizatsiyasi: navbatdagi jamoaviy hodisalarni yuborish (FR-3.1/3.2/3.4),
 * tasdiqlangan chuqur va radarlarni yuklab POI bazasiga qo'shish (FR-3.3, FR-2.1).
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HamrohApp
        val api = app.api
        if (!api.configured) return Result.success()
        return try {
            // 1) yuborish
            while (true) {
                val batch = app.db.pendingCrowd(500)
                if (batch.isEmpty()) break
                api.postCrowd(batch)
                app.db.deleteCrowd(batch.last().id)
            }
            // 2) tasdiqlangan nuqtalar
            val center = LatLon(app.prefs.poiCenterLat.toDouble(), app.prefs.poiCenterLon.toDouble())
            if (center.lat != 0.0) {
                val since = app.prefs.crowdSyncedAt
                val pts = api.crowdPoints(Geo.bbox(center, 15_000.0), since)
                val pois = pts.filter { it.confirmed }.mapNotNull { p ->
                    val type = when (p.type) {
                        "pothole" -> PoiType.POTHOLE
                        "report_camera" -> PoiType.SPEED_CAMERA
                        else -> null
                    } ?: return@mapNotNull null
                    Poi("crowd-${p.id}", type, p.lat, p.lon)
                }
                app.db.upsertPois(pois, "crowd")
                app.prefs.crowdSyncedAt = System.currentTimeMillis() / 1000
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("SyncWorker", "sinxronlash xatosi", e)
            Result.retry()
        }
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(net).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun now(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
