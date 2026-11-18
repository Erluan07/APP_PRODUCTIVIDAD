package com.kronos.focus.services

import android.Manifest
import android.annotation.SuppressLint
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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.kronos.focus.R
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.domain.sleep.Epoch
import com.kronos.focus.domain.sleep.SleepModel
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Despertar inteligente v2. Cada 30 s resume el movimiento (acelerómetro) y, si hay permiso y el usuario
 * inició el seguimiento, un sondeo de 2 s del micrófono (solo se mide el volumen; el audio se descarta al
 * instante, nunca se guarda). Dentro de la ventana previa a la alarma evalúa [SleepModel] y, si procede, hace sonar la alarma.
 *
 * Se inicia con "Me voy a dormir" (toda la noche, con micrófono y ciclo de sueño) o, como respaldo, desde
 * la alarma al abrirse la ventana (solo movimiento, sin ciclo).
 */
class SleepTrackService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val mic = Executors.newSingleThreadExecutor()
    private var sm: SensorManager? = null
    private var wl: PowerManager.WakeLock? = null
    private var started = false
    private var micEnabled = false
    private var onsetMs: Long? = null
    private var deadlineMs = 0L
    private var stamp: String? = null
    private var last = FloatArray(3)
    private var hasLast = false
    @Volatile private var moves = 0
    @Volatile private var soundEvent = false
    @Volatile private var micValid = false
    private val epochs = ArrayDeque<Epoch>()
    private val dbHistory = ArrayDeque<Double>()
    private val params by lazy { SleepLogStore.get(this).params() }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            epochs.addLast(Epoch(moves, soundEvent, micValid))
            if (epochs.size > 120) epochs.removeFirst()
            moves = 0; soundEvent = false
            if (micEnabled) mic.execute { sampleMic() }
            val toDeadline = (deadlineMs - now) / 60_000.0
            val sinceOnset = onsetMs?.let { (now - it) / 60_000.0 }
            if (SleepModel.shouldWake(epochs.toList(), sinceOnset, toDeadline, params)) {
                AlarmFlow.ring(this@SleepTrackService, smart = true, stamp = stamp)
                return
            }
            if (now > deadlineMs + 120_000L) { stopSelf(); return }
            handler.postDelayed(this, EPOCH_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true
        running = true
        val fromUser = intent?.getBooleanExtra(EXTRA_USER, false) ?: false
        micEnabled = fromUser && AppRulesConfig.SLEEP_USE_MICROPHONE &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val now = System.currentTimeMillis()
        onsetMs = if (fromUser) now + SLEEP_LATENCY_MIN * 60_000L else null
        SleepLogStore.get(this).setOnset(onsetMs)
        val occ = AlarmScheduler.nextOccurrence(this, now)
        deadlineMs = occ?.timeMs ?: 0L
        stamp = occ?.stamp

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("smart", "Despertar inteligente", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "smart")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (fromUser) "Registrando tu sueño para despertarte en el mejor momento" else "Buscando el mejor momento para despertarte")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or (if (micEnabled) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            startForeground(3, n, type)
        } else startForeground(3, n)
        // Sin alarmas activas no hay a qué hora despertarte (se comprueba después de mostrar la notificación).
        if (occ == null) { stopSelf(); return START_NOT_STICKY }

        val hold = ((deadlineMs - now) + 10 * 60_000L).coerceIn(10 * 60_000L, 14 * 3_600_000L)
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kronos:sleeptrack").apply { acquire(hold) }
        sm = getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (acc == null) { stopSelf(); return START_NOT_STICKY }
        sm?.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed(tick, EPOCH_MS)
        return START_NOT_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (hasLast) {
            val d = abs(e.values[0] - last[0]) + abs(e.values[1] - last[1]) + abs(e.values[2] - last[2])
            if (d > AppRulesConfig.SMART_MOVE_DELTA) moves++
        }
        last = e.values.clone(); hasLast = true
    }

    /** Mide 2 s de volumen (dB) y lo compara con el ruido de fondo de la noche. Nada se guarda ni sale del teléfono. */
    @SuppressLint("MissingPermission")
    private fun sampleMic() {
        val rate = 8000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { micValid = false; return }
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { rec?.release(); micValid = false; return }
        val buf = ShortArray(rate * 2)
        var read = 0
        try {
            rec.startRecording()
            while (read < buf.size) {
                val n = rec.read(buf, read, buf.size - read)
                if (n <= 0) break
                read += n
            }
        } catch (_: Exception) {
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        if (read < rate) { micValid = false; return }
        var sum = 0.0
        for (i in 0 until read) sum += buf[i].toDouble() * buf[i]
        val db = 20 * log10(sqrt(sum / read) / 32768.0 + 1e-9)
        val base = dbHistory.sorted().let { if (it.isEmpty()) db else it[it.size / 2] }
        soundEvent = dbHistory.size >= 10 && db > base + 6.0
        micValid = true
        dbHistory.addLast(db)
        if (dbHistory.size > 60) dbHistory.removeFirst()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { sm?.unregisterListener(this) }
        mic.shutdownNow()
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_USER = "user"
        private const val EPOCH_MS = 30_000L
        private const val SLEEP_LATENCY_MIN = 15L
        @Volatile var running = false

        fun start(ctx: Context, fromUser: Boolean) {
            if (running) return
            runCatching { ctx.startForegroundService(Intent(ctx, SleepTrackService::class.java).putExtra(EXTRA_USER, fromUser)) }
        }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, SleepTrackService::class.java)) } }
    }
}
