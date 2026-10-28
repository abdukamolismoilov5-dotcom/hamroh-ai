package uz.hamroh.app.drive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import uz.hamroh.app.ui.MainActivity
import uz.hamroh.app.util.T
import kotlin.math.abs

/**
 * FR-1.3: boshqa ilovalar (Yandex Navigator) ustidagi kichik suzuvchi oyna: tezlik, cheklov, oxirgi ogohlantirish.
 * Sudrab joylashtiriladi; bir bosish — ilovani ochadi; uzoq bosish — yashiradi.
 */
class Overlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private lateinit var speed: TextView
    private lateinit var limit: TextView
    private lateinit var alert: TextView
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 240 }

    val canShow get() = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show() {
        if (root != null || !canShow) return
        val dp = context.resources.displayMetrics.density
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(Color.argb(240, 10, 10, 16))
                setStroke((1.5 * dp).toInt(), Color.rgb(56, 189, 248))
            }
        }
        speed = TextView(context).apply { setTextColor(Color.WHITE); textSize = 26f; text = "0" }
        limit = TextView(context).apply { setTextColor(Color.rgb(56, 189, 248)); textSize = 13f; text = "" }
        alert = TextView(context).apply { setTextColor(Color.WHITE); textSize = 12f; maxWidth = (170 * dp).toInt(); text = "Hamroh" }
        box.addView(speed); box.addView(limit); box.addView(alert)
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var downAt = 0L
        box.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; downAt = e.eventTime; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt(); params.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(v, params) }; true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(e.rawX - downX) > 12 || abs(e.rawY - downY) > 12
                    if (!moved) {
                        if (e.eventTime - downAt > 600) hide()
                        else context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    true
                }
                else -> false
            }
        }
        runCatching { wm.addView(box, params); root = box }
    }

    @SuppressLint("SetTextI18n")
    fun render(ui: DriveUi, alertText: String?) {
        if (root == null) return
        speed.text = "${ui.speedKmh.toInt()}"
        val over = ui.limitKmh != null && ui.speedKmh > ui.limitKmh + 5
        speed.setTextColor(if (over) Color.rgb(255, 99, 88) else Color.WHITE)
        limit.text = buildString {
            ui.limitKmh?.let { append(T("cheklov ", "огр. ", "limit ")).append(it) }
            if (ui.inSchoolZone) { if (isNotEmpty()) append(" · "); append(T("maktab", "школа", "school")) }
        }
        limit.visibility = if (limit.text.isEmpty()) View.GONE else View.VISIBLE
        alertText?.let { alert.text = it }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }
}
