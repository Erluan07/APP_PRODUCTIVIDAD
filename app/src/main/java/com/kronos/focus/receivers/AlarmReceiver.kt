package com.kronos.focus.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.services.AlarmFlow
import com.kronos.focus.services.BunkerWatchdogService
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.services.SleepReminder
import com.kronos.focus.services.SleepTrackService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = AlarmStateStore.get(context)
        when (intent.action) {
            AlarmScheduler.ACTION_DEADLINE -> AlarmFlow.ring(context)
            AlarmScheduler.ACTION_TEST -> AlarmFlow.ring(context, test = true)
            AlarmScheduler.ACTION_CHECK -> AlarmFlow.startCheck(context)
            AlarmScheduler.ACTION_BLOCK -> BunkerWatchdogService.start(context)
            AlarmScheduler.ACTION_SLEEP -> {
                SleepReminder.post(context)
                BunkerWatchdogService.start(context)
                AlarmScheduler.scheduleSleep(context)
            }
            AlarmScheduler.ACTION_SMART ->
                if (s.lastRingDay != AlarmScheduler.epochDay() && s.phase == AlarmPhase.IDLE) SleepTrackService.start(context, fromUser = false)
        }
    }
}
