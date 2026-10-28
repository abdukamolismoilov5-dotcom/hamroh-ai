package uz.hamroh.app.auto

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import uz.hamroh.app.HamrohApp
import uz.hamroh.app.R
import uz.hamroh.app.drive.DriveService
import uz.hamroh.app.util.T

/**
 * FR-1.1: avtomatik ishga tushish.
 * - Mashina Bluetooth'iga ulanish (ACL_CONNECTED) → xizmat yonadi; uzilish → kutish rejimiga signal.
 * - Activity Recognition: IN_VEHICLE ENTER → xizmat yonadi.
 * Android 12+ fon cheklovi tufayli xizmat ishga tushmasa, "Haydashni boshlash" bildirishnomasi ko'rsatiladi (bir bosish).
 */
object AutoStart {
    private const val TAG = "AutoStart"
    const val NOTIF_ID = 4101

    fun startDriving(context: Context, reason: String) {
        val app = context.applicationContext as HamrohApp
        if (!app.prefs.consentAccepted || !app.prefs.autoStart) return
        try {
            DriveService.start(context, reason)
        } catch (e: Exception) {
            Log.w(TAG, "fon cheklovi: $reason", e)
            showStartPrompt(context)
        }
    }

    @SuppressLint("MissingPermission")
    fun showStartPrompt(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getForegroundService(
            context, 1, DriveService.intent(context, "notification"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, HamrohApp.CH_AUTOSTART)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(T("Mashinadasizmi?", "Вы в машине?", "In the car?"))
            .setContentText(T("Hamroh'ni yoqish uchun bosing", "Нажмите, чтобы включить Hamroh", "Tap to start Hamroh"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
    }

    @SuppressLint("MissingPermission")
    fun registerActivityTransitions(context: Context) {
        val perm = if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACTIVITY_RECOGNITION else "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) return
        val req = ActivityTransitionRequest(listOf(
            ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
            ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
        ))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(context, 7, Intent(context, ActivityTransitionReceiver::class.java), flags)
        ActivityRecognition.getClient(context).requestActivityTransitionUpdates(req, pi)
            .addOnFailureListener { Log.w(TAG, "activity transitions", it) }
    }
}

class AutoStartReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HamrohApp
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val addr = runCatching { device?.address }.getOrNull() ?: return
                if (addr != app.prefs.carBluetoothAddress) return
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) AutoStart.startDriving(context, "bluetooth")
                else DriveService.signal(context, DriveService.ACTION_CAR_DISCONNECTED)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AutoStart.registerActivityTransitions(context)
        }
    }
}

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (e in result.transitionEvents) {
            if (e.activityType != DetectedActivity.IN_VEHICLE) continue
            if (e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) AutoStart.startDriving(context, "activity")
        }
    }
}
