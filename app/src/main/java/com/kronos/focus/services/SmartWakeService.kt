package com.kronos.focus.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.kronos.focus.R
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import kotlin.math.abs

/**
 * Smart Wake-Up: durante la ventana previa a la hora límite vigila micro-movimientos con el
 * acelerómetro (sueño ligero) y, al detectarlos, dispara la alarma antes de la hora límite.
 * Heurística simple: en cada época de 30 s cuenta lecturas con cambio > SMART_MOVE_DELTA.
 */
class SmartWakeService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sm: SensorManager
    private var wl: PowerManager.WakeLock? = null
    private var last = FloatArray(3)
    private var hasLast = false
    private var moves = 0
    private var epochStart = 0L
    private var fired = false

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("smart", "Despertar inteligente", NotificationManager.IMPORTANCE_LOW))
        startForeground(3, Notification.Builder(this, "smart")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Buscando el mejor momento para despertarte")
            .setOngoing(true).build())
        sm = getSystemService(SensorManager::class.java)
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:smartwake")
            .apply { acquire((AppRulesConfig.SMART_WAKE_WINDOW_MIN + 5) * 60_000L) }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            ?: stopSelf()
        epochStart = System.currentTimeMillis()
        // Seguridad: si la hora límite ya pasó o no hay movimiento, la alarma exacta se encarga; aquí paramos.
        handler.postDelayed({ stopSelf() }, (AppRulesConfig.SMART_WAKE_WINDOW_MIN + 2) * 60_000L)
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (fired) return
        if (hasLast) {
            val d = abs(e.values[0] - last[0]) + abs(e.values[1] - last[1]) + abs(e.values[2] - last[2])
            if (d > AppRulesConfig.SMART_MOVE_DELTA) moves++
        }
        last = e.values.clone(); hasLast = true
        val now = System.currentTimeMillis()
        if (now - epochStart >= 30_000L) {
            // Ignora los primeros 2 minutos (el usuario acaba de dejar el móvil).
            val warm = now - serviceStart < 120_000L
            if (!warm && moves >= AppRulesConfig.SMART_MOVE_COUNT) {
                fired = true
                AlarmFlow.ring(this)
                stopSelf()
                return
            }
            moves = 0; epochStart = now
        }
    }

    private val serviceStart = System.currentTimeMillis()

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { sm.unregisterListener(this) }
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) = runCatching { ctx.startForegroundService(Intent(ctx, SmartWakeService::class.java)) }
    }
}
