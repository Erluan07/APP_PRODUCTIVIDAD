package com.kronos.focus.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.data.RoutineStore
import com.kronos.focus.receivers.TestExitReceiver

/**
 * Modo prueba: una ventana corta (solo si no hay nada activo) para probar la alarma, la rutina y el Modo Dormir
 * con salida rápida. Mientras dura hay un botón "Salir" en la pantalla de alarma/rutina y una notificación fija
 * con "Salir del Modo prueba". Al salir, o al vencer el tiempo, se limpia todo lo que la prueba dejó activo.
 */
object TestMode {
    const val DURATION_MIN = 30L
    private const val CHANNEL = "testmode"
    private const val NOTIF_ID = 20

    private fun prefs(ctx: Context) = ctx.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences("test_mode", Context.MODE_PRIVATE)

    fun until(ctx: Context): Long = prefs(ctx).getLong("until", 0L)
    fun active(ctx: Context): Boolean = until(ctx) > System.currentTimeMillis()

    /** Solo se puede activar con todo en reposo: no sirve para escapar de un bloqueo en curso. */
    fun canStart(ctx: Context): Boolean =
        !active(ctx) && AlarmStateStore.get(ctx).phase == AlarmPhase.IDLE && !BunkerStateStore.get(ctx).isActive()

    fun start(ctx: Context): Boolean {
        if (!canStart(ctx)) return false
        prefs(ctx).edit().putLong("until", System.currentTimeMillis() + DURATION_MIN * 60_000L).apply()
        notifyExit(ctx)
        return true
    }

    /** Si el tiempo venció, limpia todo (nunca debe quedar algo bloqueado por una prueba olvidada). */
    fun checkExpiry(ctx: Context) {
        val u = until(ctx)
        if (u != 0L && u <= System.currentTimeMillis()) exitAll(ctx)
    }

    /** Salida rápida: apaga el Modo prueba y todo lo que la prueba pudo dejar activo. */
    fun exitAll(ctx: Context) {
        val app = ctx.applicationContext
        prefs(app).edit().putLong("until", 0L).apply()
        runCatching { app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID) }
        val s = AlarmStateStore.get(app)
        s.phase = AlarmPhase.IDLE
        s.muteUntilMs = 0L; s.muteUses = 0; s.routineDeadlineMs = 0L; s.ritualDeadlineMs = 0L; s.ringNote = ""
        RoutineStore.get(app).reset()
        BunkerStateStore.get(app).forceEnd()
        AgendaStore.get(app).resetDayForTest()
        AwakeWatchService.stop(app)
        SleepTrackService.stop(app)
        AlarmService.sync(app)            // sin fase activa, el servicio se apaga solo
        AlarmScheduler.scheduleDaily(app) // las alarmas reales siguen programadas
    }

    private fun notifyExit(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Modo prueba", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getBroadcast(
            ctx, 20, Intent(ctx, TestExitReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(NOTIF_ID, Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Modo prueba activo")
            .setContentText("Toca para salir y limpiar todo")
            .setContentIntent(pi)
            .addAction(Notification.Action.Builder(null, "Salir del Modo prueba", pi).build())
            .setOngoing(true).build())
    }
}
