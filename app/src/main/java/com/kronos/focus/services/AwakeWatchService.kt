package com.kronos.focus.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.VibrationEffect
import android.os.Vibrator
import com.kronos.focus.R
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import kotlin.math.abs

/**
 * Anti-recaída v2. Empieza en cuanto escaneas el QR y dura hasta [AppRulesConfig.AWAKE_WATCH_MIN] min después del
 * Wake-Up Check. Lo que cuenta es CAMINAR (sensor de pasos), no usar el teléfono: en cada ventana de
 * [AppRulesConfig.AWAKE_WINDOW_MIN] min tienes que dar [AppRulesConfig.AWAKE_MIN_STEPS] pasos. Si no, aviso fuerte y, tras
 * [AppRulesConfig.AWAKE_GRACE_SEC] s más sin caminar, la alarma vuelve a sonar completa.
 * Sin permiso o sensor de pasos usa el movimiento del acelerómetro como respaldo.
 */
class AwakeWatchService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var sm: SensorManager? = null
    private var wl: PowerManager.WakeLock? = null
    private var started = false
    private var stepMode = false
    private var endMs = 0L
    private var windowStartMs = 0L
    private var graceUntilMs = 0L
    private var warned = false
    @Volatile private var steps = 0
    private var last = FloatArray(3)
    private var hasLast = false
    @Volatile private var moves = 0

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (AlarmStateStore.get(this@AwakeWatchService).phase == AlarmPhase.RINGING) { stopSelf(); return } // ya está sonando
            if (now >= endMs) { stopSelf(); return }
            if (!stepMode && moves >= AppRulesConfig.AWAKE_MOVE_COUNT) steps += 5
            moves = 0
            when {
                steps >= AppRulesConfig.AWAKE_MIN_STEPS -> { steps = 0; windowStartMs = now; warned = false } // caminaste: ventana nueva
                !warned && now - windowStartMs >= AppRulesConfig.AWAKE_WINDOW_MIN * 60_000L -> {
                    warned = true
                    graceUntilMs = now + AppRulesConfig.AWAKE_GRACE_SEC * 1000L
                    warn()
                }
                warned && now >= graceUntilMs -> { AlarmFlow.relapse(this@AwakeWatchService); stopSelf(); return }
            }
            handler.postDelayed(this, 10_000L)
        }
    }

    private fun warn() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(5, Notification.Builder(this, "awake_warn")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("¡Camina ahora!")
            .setContentText("Si no caminas en 1 minuto, la alarma vuelve a sonar.")
            .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true).build())
        runCatching {
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val until = intent?.getLongExtra(EXTRA_END, 0L) ?: 0L
        val now = System.currentTimeMillis()
        endMs = if (until > now) until else now + AppRulesConfig.AWAKE_WATCH_MIN * 60_000L
        windowStartMs = now; steps = 0; warned = false
        if (started) return START_NOT_STICKY   // reinicio de la vigilancia (nuevo escaneo): solo se actualizan fin y ventana
        started = true; running = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("awake", "Vigilando que sigas despierto", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("awake_warn", "Aviso: camina", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "awake")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Vigilando que sigas despierto: camina un poco cada ${AppRulesConfig.AWAKE_WINDOW_MIN} min")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(4, n)
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:awakewatch")
            .apply { acquire((AppRulesConfig.AWAKE_WATCH_MIN + 25) * 60_000L) }
        sm = getSystemService(SensorManager::class.java)
        val stepSensor = if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
            sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null
        if (stepSensor != null) { stepMode = true; sm?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL) }
        else sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        handler.postDelayed(tick, 10_000L)
        return START_NOT_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_STEP_DETECTOR) { steps++; return }
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
        runCatching { sm?.unregisterListener(this) }
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_END = "end"
        @Volatile var running = false

        /** Vigila hasta [endMs]. Si ya estaba corriendo, solo reinicia la ventana y el fin. */
        fun start(ctx: Context, endMs: Long = 0L) {
            runCatching { ctx.startForegroundService(Intent(ctx, AwakeWatchService::class.java).putExtra(EXTRA_END, endMs)) }
        }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, AwakeWatchService::class.java)) } }
    }
}
