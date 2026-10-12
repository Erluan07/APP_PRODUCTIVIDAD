package com.kronos.focus.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.receivers.AlarmReceiver
import com.kronos.focus.ui.MainActivity
import java.time.LocalDate
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_DEADLINE = "com.kronos.focus.ALARM_DEADLINE"
    const val ACTION_SMART = "com.kronos.focus.ALARM_SMART_START"
    const val ACTION_CHECK = "com.kronos.focus.ALARM_WAKE_CHECK"
    const val ACTION_TEST = "com.kronos.focus.ALARM_TEST"

    fun epochDay(): Long = LocalDate.now().toEpochDay()

    fun nextDeadline(now: Long, skipToday: Boolean): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, AppRulesConfig.ALARM_HOUR)
            set(Calendar.MINUTE, AppRulesConfig.ALARM_MINUTE)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        if (skipToday && sameDay) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
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
    }

    fun scheduleCheck(ctx: Context, atMs: Long) {
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, ACTION_CHECK)) }
    }

    fun scheduleTest(ctx: Context, atMs: Long) {
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, ACTION_TEST)) }
    }

    fun cancelRing(ctx: Context) {
        am(ctx).cancel(pi(ctx, ACTION_DEADLINE)); am(ctx).cancel(pi(ctx, ACTION_SMART))
    }
}
