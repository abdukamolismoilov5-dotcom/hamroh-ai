package uz.hamroh.app.drive

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import uz.hamroh.core.Thresholds
import uz.hamroh.core.model.ImuSample
import kotlin.math.sqrt

/**
 * Akselerometr (50 Hz) va gravitatsiya sensori. Xom o'lchovdan yadro uchun [ImuSample] hisoblaydi:
 * totalG — umumiy modul (g), verticalMs2 — gravitatsiya yo'nalishidagi chiziqli tezlanish.
 * Gravitatsiya sensori bo'lmasa, past chastotali filtr bilan baholanadi.
 */
class SensorHub(
    context: Context,
    private val onImu: (ImuSample) -> Unit,
    private val onGravity: (Double, Double, Double) -> Unit,
) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val g = DoubleArray(3)
    private var hasGravitySensor = false
    private var gravityInit = false
    private var lastGravityPush = 0L

    fun start() {
        var t = thread
        if (t == null || !t.isAlive) {
            t = HandlerThread("imu").apply { start() }
            thread = t
            handler = Handler(t.looper)
        }
        val h = handler
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        hasGravitySensor = grav != null
        acc?.let { sm.registerListener(this, it, 20_000, h) }
        grav?.let { sm.registerListener(this, it, 100_000, h) }
    }

    fun stop() {
        runCatching { sm.unregisterListener(this) }
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                g[0] = e.values[0].toDouble(); g[1] = e.values[1].toDouble(); g[2] = e.values[2].toDouble()
                gravityInit = true
                pushGravity()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = e.values[0].toDouble(); val ay = e.values[1].toDouble(); val az = e.values[2].toDouble()
                if (!hasGravitySensor) {
                    val a = if (gravityInit) 0.05 else 1.0
                    g[0] += a * (ax - g[0]); g[1] += a * (ay - g[1]); g[2] += a * (az - g[2])
                    gravityInit = true
                    pushGravity()
                }
                val total = sqrt(ax * ax + ay * ay + az * az)
                val gn = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).coerceAtLeast(1e-3)
                val up = (ax * g[0] + ay * g[1] + az * g[2]) / gn
                onImu(ImuSample(SystemClock.elapsedRealtime(), total / Thresholds.G, up - Thresholds.G))
            }
        }
    }

    private fun pushGravity() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGravityPush < 500) return
        lastGravityPush = now
        onGravity(g[0], g[1], g[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
