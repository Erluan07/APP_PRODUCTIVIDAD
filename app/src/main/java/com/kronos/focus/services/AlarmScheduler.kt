package com.kronos.focus.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.AlarmConfigStore
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.domain.alarm.AlarmTimes
import com.kronos.focus.receivers.AlarmReceiver
import com.kronos.focus.ui.MainActivity
import java.time.LocalDate

object AlarmScheduler {
    const val ACTION_DEADLINE = "com.kronos.focus.ALARM_DEADLINE"
    const val ACTION_SMART = "com.kronos.focus.ALARM_SMART_START"
    const val ACTION_CHECK = "com.kronos.focus.ALARM_WAKE_CHECK"
    const val ACTION_BLOCK = "com.kronos.focus.BUNKER_BLOCK_START"
    const val EXTRA_STAMP = "stamp"

    fun epochDay(): Long = LocalDate.now().toEpochDay()

    /** Próxima alarma (de las que tienes activas) que aún no ha sonado, o null si no hay ninguna. */
    fun nextOccurrence(ctx: Context, now: Long = System.currentTimeMillis()): AlarmTimes.Occurrence? =
        AlarmTimes.next(AlarmConfigStore.get(ctx).all(), now, AlarmStateStore.get(ctx).lastRingStamp)

    private fun pi(ctx: Context, action: String, stamp: String? = null) = PendingIntent.getBroadcast(
        ctx, action.hashCode(),
        Intent(ctx, AlarmReceiver::class.java).setAction(action).apply { stamp?.let { putExtra(EXTRA_STAMP, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    /** Programa la próxima alarma (reloj del sistema) y el inicio de la ventana del despertar inteligente. */
    fun scheduleDaily(ctx: Context) {
        val now = System.currentTimeMillis()
        val occ = nextOccurrence(ctx, now)
        if (occ == null) { cancelRing(ctx); return }
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        runCatching { am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(occ.timeMs, show), pi(ctx, ACTION_DEADLINE, occ.stamp)) }
        val smartAt = occ.timeMs - SleepLogStore.get(ctx).params().windowMin * 60_000L
        val at = if (smartAt > now) smartAt else now + 2_000L
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, ACTION_SMART, occ.stamp)) }
    }

    fun scheduleCheck(ctx: Context, atMs: Long) {
        runCatching { am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, ACTION_CHECK)) }
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
