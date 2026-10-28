package uz.hamroh.app.drive

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import uz.hamroh.app.util.Lang
import uz.hamroh.core.model.AlertLevel

/**
 * FR-10: oflayn ovozli ogohlantirishlar. res/raw/<til>_<kalit>.ogg fayllari (tools/make_voice.py yoki studiya yozuvlari).
 * Fayl topilmasa — signal. Navigator ovozi vaqtincha pasaytiriladi (TRANSIENT_MAY_DUCK, FR-10.3).
 * [lastLatencyMs] — so'rovdan ovoz boshlanguncha bo'lgan vaqt (TZ 4-bo'lim o'lchovi uchun).
 */
class VoicePlayer(private val context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 95) }.getOrNull()
    @Volatile var lastLatencyMs: Long = -1
        private set

    /** [onDone] — o'ynash tugaganda (asosiy oqimda). */
    fun play(voiceKey: String, level: AlertLevel, onDone: () -> Unit) {
        val requested = SystemClock.elapsedRealtime()
        main.post {
            stopInternal()
            val resId = resolve(voiceKey)
            if (resId == 0) {
                // Agar maxsus ovoz fayli topilmasa, signal berish
                if (level == AlertLevel.TICK) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                else tone?.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                main.postDelayed(onDone, 400)
                return@post
            }

            requestFocus()
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setVolume(1.0f, 1.0f)
                context.resources.openRawResourceFd(resId).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                mp.setOnCompletionListener { abandonFocus(); it.release(); if (player === it) player = null; onDone() }
                mp.setOnErrorListener { p, _, _ -> abandonFocus(); p.release(); if (player === p) player = null; onDone(); true }
                mp.prepare()
                mp.start()
                lastLatencyMs = SystemClock.elapsedRealtime() - requested
                player = mp
            } catch (e: Exception) {
                Log.w("Voice", "ovoz xatosi $voiceKey", e)
                runCatching { mp.release() }
                abandonFocus()
                onDone()
            }
        }
    }

    /** Yuqori ustuvorlikdagi xabar uchun joriy ovozni to'xtatish. */
    fun stop() { main.post { stopInternal() } }

    private fun stopInternal() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun resolve(key: String): Int {
        val res = context.resources
        for (lang in listOf(Lang.current, "uz")) {
            @Suppress("DiscouragedApi")
            val id = res.getIdentifier("${lang}_$key", "raw", context.packageName)
            if (id != 0) return id
        }
        return 0
    }

    private fun requestFocus() {
        runCatching {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < (maxVol * 0.75).toInt()) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)
            }
        }
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        focus = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() { focus?.let { am.abandonAudioFocusRequest(it) }; focus = null }

    fun release() { main.post { stopInternal(); tone?.release() } }
}
