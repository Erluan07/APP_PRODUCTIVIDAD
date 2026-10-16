package com.kronos.focus.services

import android.content.Context
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig

/** Activación manual del Modo Dormir: dura hasta tu próxima alarma (o el tope de horas) y termina cuando suena. */
object SleepMode {
    fun begin(ctx: Context) {
        val now = System.currentTimeMillis()
        val cap = now + AppRulesConfig.SLEEP_MAX_HOURS * 3_600_000L
        val end = minOf(AlarmScheduler.nextOccurrence(ctx, now)?.timeMs ?: cap, cap)
        BunkerStateStore.get(ctx).startSleepMode(end, now)
        BunkerWatchdogService.start(ctx)
    }
}
