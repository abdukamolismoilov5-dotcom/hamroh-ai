package uz.hamroh.app.care

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.R
import uz.hamroh.app.util.T
import uz.hamroh.core.care.CarCare
import uz.hamroh.core.care.DocKind
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** FR-9: kuniga bir marta moy va hujjat muddatlarini tekshiradi, kerak bo'lsa eslatma beradi. */
class CareWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HamrohApp
        val p = app.prefs
        // moy
        if (p.lastOilKm > 0f) {
            val left = CarCare.oilKmLeft(p.odometerKm.toDouble(), p.lastOilKm.toDouble(), p.oilIntervalKm)
            val notified = p.oilNotifiedAtKmLeft.takeIf { !it.isNaN() }?.toDouble()
            if (CarCare.oilReminderDue(left, notified)) {
                p.oilNotifiedAtKmLeft = left.toFloat()
                notify(app, 9001,
                    T("Moy almashtirish vaqti", "Пора менять масло", "Oil change due"),
                    if (left > 0) T("${left.toInt()} km qoldi", "Осталось ${left.toInt()} км", "${left.toInt()} km left")
                    else T("Muddat o'tib ketdi: ${(-left).toInt()} km", "Просрочено на ${(-left).toInt()} км", "Overdue by ${(-left).toInt()} km"))
            }
        }
        // hujjatlar
        val today = LocalDate.now().toEpochDay()
        DocKind.entries.forEachIndexed { i, kind ->
            val due = p.docDate(kind.name)
            if (due == 0L) return@forEachIndexed
            val last = p.docStage(kind.name).takeIf { it > 0 }
            val stage = CarCare.docStageDue(due - today, last) ?: return@forEachIndexed
            p.setDocStage(kind.name, stage)
            notify(app, 9100 + i, docTitle(kind), T("Muddat: ${due - today} kun qoldi", "Срок: осталось ${due - today} дн.", "Due in ${due - today} days"))
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(ctx).notify(id, NotificationCompat.Builder(ctx, HamrohApp.CH_REMIND)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setAutoCancel(true).build())
    }

    companion object {
        fun docTitle(k: DocKind) = when (k) {
            DocKind.OSAGO -> T("OSAGO sug'urtasi", "Страховка ОСАГО", "OSAGO insurance")
            DocKind.INSPECTION -> T("Texnik ko'rik", "Техосмотр", "Technical inspection")
            DocKind.GAS_CERT -> T("Gaz ballon attestatsiyasi", "Аттестация газового баллона", "Gas cylinder certificate")
            DocKind.PROXY -> T("Ishonchnoma", "Доверенность", "Power of attorney")
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "care", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<CareWorker>(1, TimeUnit.DAYS).build())
        }
    }
}
