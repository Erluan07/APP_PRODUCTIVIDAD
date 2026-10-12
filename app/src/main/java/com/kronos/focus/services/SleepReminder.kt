package com.kronos.focus.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kronos.focus.ui.MainActivity

/** Aviso de que empieza el Modo Dormir (y recordatorio de pulsar "Me voy a dormir"). */
object SleepReminder {
    fun post(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sleep", "Modo Dormir", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(20, Notification.Builder(ctx, "sleep")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de dormir")
            .setContentText("Modo Dormir activo. Abre Kronos y pulsa “Me voy a dormir”.")
            .setContentIntent(pi).setAutoCancel(true).build())
    }
}
