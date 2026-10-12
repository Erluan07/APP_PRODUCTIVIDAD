package com.kronos.focus.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.receivers.AlarmReceiver
import com.kronos.focus.ui.MainActivity
import android.net.Uri
import com.kronos.focus.data.AgendaStore
import java.time.LocalDate
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_DEADLINE = "com.kronos.focus.ALARM_DEADLINE"
    const val ACTION_SMART = "com.kronos.focus.ALARM_SMART_START"
    const val ACTION_CHECK = "com.kronos.focus.ALARM_WAKE_CHECK"
    const val ACTION_BLOCK = "com.kronos.focus.BUNKER_BLOCK_START"
    const val ACTION_SLEEP = "com.kronos.focus.SLEEP_MODE_START"
    const val ACTION_TEST = "com.kronos.focus.ALARM_TEST"

    fun epochDay(): Long = LocalDate.now().toEpochDay()

    /** Próxima hora de alarma según la configuración por día de la semana (null en un día = sin alarma). */
    fun nextDeadline(now: Long, skipToday: Boolean): Long {
        for (offset in 0..7) {
            val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
            val (h, m) = AppRulesConfig.alarmAt(c.get(Calendar.DAY_OF_WEEK)) ?: continue
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now) continue
            if (skipToday && offset == 0) continue
            return c.timeInMillis
        }
        return now + 24 * 3_600_000L // sin alarmas configuradas
    }

    /** Próximo inicio del Modo Dormir (hora local fija). */
    fun nextSleepStart(now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, AppRulesConfig.SLEEP_START_MIN / 60); set(Calendar.MINUTE, AppRulesConfig.SLEEP_START_MIN % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun scheduleSleep(ctx: Context) {
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextSleepStart(System.currentTimeMillis()), pi(ctx, ACTION_SLEEP)) }
    }

    private fun pi(ctx: Context, action: String) = PendingIntent.getBroadcast(
        ctx, action.hashCode(), Intent(ctx, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    /** Programa la hora límite (alarma "de reloj") y el inicio de la ventana de Smart Wake-Up. */
    fun scheduleDaily(ctx: Context) {
        val now = System.currentTimeMillis()
        val skip = AlarmStateStore.get(ctx).lastRingDay == epochDay()
        val deadline = nextDeadline(now, skip)
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        runCatching { am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(deadline, show), pi(ctx, ACTION_DEADLINE)) }
        val smartAt = deadline - AppRulesConfig.SMART_WAKE_WINDOW_MIN * 60_000L
        val at = if (smartAt > now) smartAt else now + 2_000L
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, ACTION_SMART)) }
        scheduleSleep(ctx)
    }

    fun scheduleCheck(ctx: Context, atMs: Long) {
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, ACTION_CHECK)) }
    }

    fun scheduleTest(ctx: Context, atMs: Long) {
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, ACTION_TEST)) }
    }

    /** Programa el arranque del vigilante en cada bloque futuro de la agenda (el bloqueo en sí lo decide la agenda). */
    fun scheduleBlocks(ctx: Context) {
        val now = System.currentTimeMillis()
        AgendaStore.get(ctx).futureStarts(now).forEach { start ->
            val pi = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_BLOCK).setData(Uri.parse("kronos://block/$start")),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, pi) }
        }
    }

    fun cancelRing(ctx: Context) {
        am(ctx).cancel(pi(ctx, ACTION_DEADLINE)); am(ctx).cancel(pi(ctx, ACTION_SMART))
    }
}
