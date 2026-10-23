package com.kronos.focus.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.kronos.focus.domain.rules.AppRulesConfig

/**
 * Al apagar la alarma con el QR/NFC, retoma lo que estabas escuchando en Spotify (para levantarte con música).
 * Envía la tecla "reproducir" (no "pausa/play", así no pausa si ya suena) primero directo a Spotify y, si pasados unos
 * segundos no suena nada, al sistema (que la entrega a la última app de música). No abre ninguna pantalla.
 */
object MusicResume {
    private const val SPOTIFY = "com.spotify.music"

    @Volatile private var guardFrom = 0L
    @Volatile private var lastNudge = 0L

    /**
     * Durante la rutina la música no se puede apagar: si la pausan se reanuda y el volumen no baja de la mitad.
     * Android no deja a una app bloquear los controles de Spotify, así que se corrige a los pocos segundos.
     * Lo llama AlarmService en cada ciclo mientras la fase es ROUTINE.
     */
    fun enforce(ctx: Context) {
        val now = System.currentTimeMillis()
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val floor = kotlin.math.ceil(max * AppRulesConfig.ROUTINE_MIN_VOLUME).toInt()
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < floor) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0) }
        if (now < guardFrom || now - lastNudge < 3_000L) return
        if (!am.isMusicActive) { lastNudge = now; sendToSpotify(ctx.applicationContext) }
    }

    fun play(ctx: Context) {
        guardFrom = System.currentTimeMillis() + 7_000L   // deja tiempo a que arranque la primera vez
        val app = ctx.applicationContext
        val handler = Handler(Looper.getMainLooper())
        // Se espera un instante a que el sonido de la alarma termine de soltar el audio.
        handler.postDelayed({ sendToSpotify(app) }, 1_200L)
        handler.postDelayed({ if (!isPlaying(app)) sendToSystem(app) }, 4_500L)
    }

    private fun key(action: Int) = KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)

    private fun isPlaying(ctx: Context) = ctx.getSystemService(AudioManager::class.java)?.isMusicActive == true

    private fun sendToSpotify(ctx: Context) {
        runCatching {
            for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                ctx.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY).putExtra(Intent.EXTRA_KEY_EVENT, key(action))
                )
            }
        }
    }

    private fun sendToSystem(ctx: Context) {
        runCatching {
            val am = ctx.getSystemService(AudioManager::class.java) ?: return
            am.dispatchMediaKeyEvent(key(KeyEvent.ACTION_DOWN))
            am.dispatchMediaKeyEvent(key(KeyEvent.ACTION_UP))
        }
    }
}
