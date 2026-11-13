package com.kronos.focus.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.kronos.focus.R
import com.kronos.focus.admin.DeviceOwnerHardening
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.ui.MainActivity

/**
 * Vigila durante el búnker que el servicio de accesibilidad siga activo. Si lo apagan, intenta
 * reactivarlo (si se concedió WRITE_SECURE_SETTINGS por ADB) y avisa con una notificación.
 */
class BunkerWatchdogService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: BunkerStateStore
    private var warned = false
    private var lastMust: String? = null

    private val tick = object : Runnable {
        override fun run() {
            DeviceOwnerHardening.sync(this@BunkerWatchdogService, store.isActive())
            if (store.remainingMs() <= 0L && !store.mustActive()) { stopSelf(); return }
            AgendaStore.get(this@BunkerWatchdogService).activeMust(System.currentTimeMillis())?.let {
                if (it.name != lastMust) { lastMust = it.name; notifyMust(it.name) }
            }
            store.pomodoroTransition()?.let { notifyPomodoro(it.focus) }
            if (store.isActive() && !AccessibilityGuard.isEnabled(this@BunkerWatchdogService)) {
                if (!AccessibilityGuard.tryEnable(this@BunkerWatchdogService)) {
                    if (!warned) { warned = true; store.recordTamper(); notifyTamper() }
                } else warned = false
            } else warned = false
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = BunkerStateStore.get(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Modo Búnker", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ALERT, "Alertas", NotificationManager.IMPORTANCE_HIGH))
        startForeground(1, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Modo Búnker vigilando")
            .setOngoing(true).build())
        handler.post(tick)
    }

    private fun notifyMust(name: String) {
        val pi = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(NotificationManager::class.java).notify(6, Notification.Builder(this, ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Tarea obligatoria: $name")
            .setContentText("El teléfono queda bloqueado hasta que subas la foto de evidencia.")
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    /** Pomodoro: solo avisa; el bloqueo de apps sigue igual durante el descanso. */
    private fun notifyPomodoro(focus: Boolean) {
        val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val (title, text) = if (focus) "A enfocarse otra vez" to "Terminó el descanso: ${AppRulesConfig.POMODORO_FOCUS_MIN} min de enfoque."
        else "Descanso de ${AppRulesConfig.POMODORO_BREAK_MIN} min" to "Levántate, estira y toma agua. Las apps siguen bloqueadas."
        getSystemService(NotificationManager::class.java).notify(3, Notification.Builder(this, ALERT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    private fun notifyTamper() {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(NotificationManager::class.java).notify(2, Notification.Builder(this, ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Servicio de bloqueo desactivado")
            .setContentText("Reactívalo en Accesibilidad. Queda registrado como trampa.")
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "bunker"
        private const val ALERT = "alerts"
        fun start(ctx: Context) = runCatching {
            ctx.startForegroundService(Intent(ctx, BunkerWatchdogService::class.java))
        }
    }
}
