package com.kronos.focus.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Al apagar la alarma con el QR/NFC, retoma lo que estabas escuchando en Spotify (para levantarte con música).
 * Envía la tecla "reproducir" (no "pausa/play", así no pausa si ya suena) primero directo a Spotify y, si pasados unos
 * segundos no suena nada, al sistema (que la entrega a la última app de música). No abre ninguna pantalla.
 */
object MusicResume {
    private const val SPOTIFY = "com.spotify.music"

    fun play(ctx: Context) {
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
