package com.kronos.focus.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.kronos.focus.R
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.ui.alarm.AlarmActivity

/**
 * Motor de la alarma. Mientras la fase sea RINGING o CHECK_ACTIVE: suena en bucle por el stream de
 * alarma a volumen máximo (se re-fuerza cada 500 ms y al cambiar el volumen), vibra y mantiene la
 * pantalla de alarma al frente (la relanza si se pierde).
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: AlarmStateStore
    private lateinit var audio: AudioManager
    private var player: MediaPlayer? = null
    private var wl: PowerManager.WakeLock? = null
    private var observer: ContentObserver? = null
    private var lastLaunch = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!store.uiRequired()) { shutdown(); return }
            val now = System.currentTimeMillis()
            if (store.phase == AlarmPhase.CHECK_ACTIVE && now > store.checkDeadlineMs) AlarmFlow.escalate(this@AlarmService)
            if (store.phase == AlarmPhase.RITUAL && now > store.ritualDeadlineMs) AlarmFlow.escalateRitual(this@AlarmService)
            if (store.phase == AlarmPhase.ROUTINE && now > store.routineDeadlineMs) AlarmFlow.escalateRoutine(this@AlarmService)
            if (store.phase == AlarmPhase.ROUTINE) MusicResume.enforce(this@AlarmService)
            enforceVolume()
            syncSound()
            if (!AlarmActivity.visible && !com.kronos.focus.ui.ProofActivity.visible && now - lastLaunch > 2_500L) { lastLaunch = now; launchUi() }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = AlarmStateStore.get(this)
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarma", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (!store.uiRequired()) { shutdown(); return START_NOT_STICKY }
        if (wl == null) {
            wl = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:alarm").apply { acquire(60 * 60_000L) }
        }
        syncSound()
        if (observer == null) {
            observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = enforceVolume()
            }.also { contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, it) }
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("¡Despierta!")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
    }

    private fun launchUi() {
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    private fun enforceVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != max) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
        }
        player?.setVolume(1f, 1f)
    }

    /** Suena salvo durante el silencio temporal ("voy a escanear"); al expirar, reanuda solo. */
    private fun syncSound() {
        val phase = store.phase
        val muted = phase == AlarmPhase.RITUAL || phase == AlarmPhase.ROUTINE || (phase == AlarmPhase.RINGING && System.currentTimeMillis() < store.muteUntilMs)
        if (muted) pauseSound() else resumeSound()
    }

    private fun pauseSound() {
        runCatching { if (player?.isPlaying == true) player?.pause() }
        runCatching { getSystemService(Vibrator::class.java).cancel() }
    }

    @Suppress("DEPRECATION")
    private fun resumeSound() {
        val p = player
        if (p != null) {
            if (!p.isPlaying) { runCatching { p.start() }; vibrate() }
            return
        }
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }
        enforceVolume()
        vibrate()
    }

    private fun vibrate() {
        runCatching {
            getSystemService(Vibrator::class.java)
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
        }
    }

    private fun shutdown() {
        handler.removeCallbacks(tick)
        runCatching { player?.stop(); player?.release() }
        player = null
        runCatching { getSystemService(Vibrator::class.java).cancel() }
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        wl?.let { if (it.isHeld) it.release() }
        wl = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "alarm"
        private const val NOTIF_ID = 10
        /** Sincroniza el servicio con la fase actual (arranca o se detiene solo). */
        fun sync(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, AlarmService::class.java)) }
        }
    }
}
