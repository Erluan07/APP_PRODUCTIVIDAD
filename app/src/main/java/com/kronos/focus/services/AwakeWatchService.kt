package com.kronos.focus.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.kronos.focus.R
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import kotlin.math.abs

/**
 * Anti-recaída: después de confirmar "estoy despierto", vigila durante [AppRulesConfig.AWAKE_WATCH_MIN] min.
 * Se considera "actividad" desbloquear el teléfono, abrir/cambiar de app o moverlo (llevarlo contigo).
 * Si pasan [AppRulesConfig.AWAKE_IDLE_MIN] min sin nada de eso, vuelve a sonar la alarma.
 */
class AwakeWatchService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var sm: SensorManager? = null
    private var wl: PowerManager.WakeLock? = null
    private var started = false
    private var startMs = 0L
    private var last = FloatArray(3)
    private var hasLast = false
    @Volatile private var moves = 0

    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { noteActivity() }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (moves >= AppRulesConfig.AWAKE_MOVE_COUNT) noteActivity()
            moves = 0
            if (AlarmStateStore.get(this@AwakeWatchService).phase != AlarmPhase.IDLE) { stopSelf(); return } // otra alarma ya está en curso
            if (now - startMs >= AppRulesConfig.AWAKE_WATCH_MIN * 60_000L) { stopSelf(); return }
            if (now - lastActivity >= AppRulesConfig.AWAKE_IDLE_MIN * 60_000L) {
                AlarmFlow.relapse(this@AwakeWatchService)
                stopSelf(); return
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true; running = true
        startMs = System.currentTimeMillis(); lastActivity = startMs
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("awake", "Vigilando que sigas despierto", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "awake")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Vigilando que no te vuelvas a dormir (${AppRulesConfig.AWAKE_WATCH_MIN} min)")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(4, n)
        ContextCompat.registerReceiver(this, unlock, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:awakewatch")
            .apply { acquire((AppRulesConfig.AWAKE_WATCH_MIN + 10) * 60_000L) }
        sm = getSystemService(SensorManager::class.java)
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        handler.postDelayed(tick, 30_000L)
        return START_NOT_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (hasLast) {
            val d = abs(e.values[0] - last[0]) + abs(e.values[1] - last[1]) + abs(e.values[2] - last[2])
            if (d > AppRulesConfig.AWAKE_MOVE_DELTA) moves++
        }
        last = e.values.clone(); hasLast = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(unlock) }
        runCatching { sm?.unregisterListener(this) }
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
        @Volatile var lastActivity = 0L

        /** Cualquier uso del teléfono (la accesibilidad lo llama en cada cambio de ventana). */
        fun noteActivity() { if (running) lastActivity = System.currentTimeMillis() }

        fun start(ctx: Context) {
            if (running) return
            runCatching { ctx.startForegroundService(Intent(ctx, AwakeWatchService::class.java)) }
        }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, AwakeWatchService::class.java)) } }
    }
}
