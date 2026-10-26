package com.kronos.focus.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import com.kronos.focus.domain.rules.AppRulesConfig

/**
 * Al apagar la alarma con el QR/NFC, retoma lo que estabas escuchando en Spotify (para levantarte con música).
 * Android no deja "dar play" a otra app de una sola forma fiable, así que se prueban varias, de más a menos directa:
 *  1. Sesión de medios de Spotify (necesita el acceso a notificaciones activado en Más → Permisos).
 *  2. Tecla "reproducir" enviada directo a Spotify.
 *  3. Tecla "reproducir" al sistema (la recibe la última app de música).
 *  4. Abrir Spotify un momento (queda en segundo plano) y volver a probar 1-3.
 */
object MusicResume {
    private const val SPOTIFY = "com.spotify.music"
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var guardFrom = 0L
    @Volatile private var lastNudge = 0L
    @Volatile private var lastLaunch = 0L

    fun isPlaying(ctx: Context) = ctx.getSystemService(AudioManager::class.java)?.isMusicActive == true

    /** ¿Está activado el acceso a notificaciones de Kronos (permite controlar Spotify de forma fiable)? */
    fun hasSessionAccess(ctx: Context): Boolean =
        Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?.contains(ctx.packageName) == true

    /** Arranca la música tras el escaneo y reintenta solo hasta que suene. */
    /** Sube el volumen de la música al nivel de la rutina (el máximo) al instante. */
    private fun volumeUp(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val floor = kotlin.math.ceil(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * AppRulesConfig.ROUTINE_MIN_VOLUME).toInt()
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < floor) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0) }
    }

    fun play(ctx: Context) {
        val app = ctx.applicationContext
        volumeUp(app)   // el volumen sube de una, antes de que empiece a sonar
        guardFrom = System.currentTimeMillis() + 12_000L   // deja tiempo a que arranque la primera vez
        handler.postDelayed({ nudge(app) }, 1_200L)
        handler.postDelayed({ if (!isPlaying(app)) nudge(app) }, 4_000L)
        handler.postDelayed({ if (!isPlaying(app)) { launchSpotify(app); handler.postDelayed({ nudge(app) }, 3_000L) } }, 7_000L)
    }

    /** Botón "Reintentar música" de la pantalla de rutina. */
    fun retry(ctx: Context) {
        val app = ctx.applicationContext
        volumeUp(app)
        nudge(app)
        handler.postDelayed({ if (!isPlaying(app)) { launchSpotify(app); handler.postDelayed({ nudge(app) }, 3_000L) } }, 2_500L)
    }

    /**
     * Durante la rutina la música no se puede apagar: si la pausan se reanuda y el volumen queda en el máximo.
     * Android no deja a una app bloquear los controles de Spotify, así que se corrige a los pocos segundos.
     * Lo llama AlarmService en cada ciclo mientras la fase es ROUTINE.
     */
    fun enforce(ctx: Context) {
        val now = System.currentTimeMillis()
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        volumeUp(ctx)
        if (now < guardFrom || now - lastNudge < 3_000L || am.isMusicActive) return
        lastNudge = now
        nudge(ctx.applicationContext)
        if (now - lastLaunch > 30_000L) { launchSpotify(ctx.applicationContext) }
    }

    /** Lo que suena ahora (título, artista, carátula) leído de la sesión de medios; null sin acceso o sin nada activo. */
    data class NowPlaying(val title: String, val artist: String, val art: android.graphics.Bitmap?, val playing: Boolean)

    private fun controller(ctx: Context): android.media.session.MediaController? = runCatching {
        val sessions = ctx.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        sessions.firstOrNull { it.packageName == SPOTIFY } ?: sessions.firstOrNull()
    }.getOrNull()

    fun nowPlaying(ctx: Context): NowPlaying? {
        val c = controller(ctx) ?: return null
        val m = c.metadata ?: return null
        val title = m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return NowPlaying(title, artist, art, c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING)
    }

    /** Siguiente / anterior. No hay función de pausa a propósito. */
    fun next(ctx: Context) { runCatching { controller(ctx)?.transportControls?.skipToNext() } }
    fun previous(ctx: Context) { runCatching { controller(ctx)?.transportControls?.skipToPrevious() } }

    private fun nudge(ctx: Context) {
        sendViaSession(ctx)
        sendToSpotify(ctx)
        handler.postDelayed({ if (!isPlaying(ctx)) sendToSystem(ctx) }, 1_000L)
    }

    private fun key(action: Int) = KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)

    private fun sendViaSession(ctx: Context) {
        runCatching {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            val sessions = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
            val target = sessions.firstOrNull { it.packageName == SPOTIFY } ?: sessions.firstOrNull()
            target?.transportControls?.play()
        }
    }

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

    /** Abre Spotify para que su servicio de música arranque; la pantalla de Kronos vuelve sola al frente. */
    private fun launchSpotify(ctx: Context) {
        lastLaunch = System.currentTimeMillis()
        runCatching {
            ctx.packageManager.getLaunchIntentForPackage(SPOTIFY)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
        }
    }
}
