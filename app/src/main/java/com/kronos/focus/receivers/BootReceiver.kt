package com.kronos.focus.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kronos.focus.BuildConfig
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.services.BunkerWatchdogService

/** Boot Trap: si había búnker pendiente, lo restaura. Penalización de +60 min solo en builds release. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.kronos.focus.services.AlarmFlow.restoreAfterBoot(context)
        val store = BunkerStateStore.get(context)
        if (!store.isActive()) return
        if (!BuildConfig.DEBUG) store.applyPenalty(AppRulesConfig.BOOT_TRAP_PENALTY_MIN)
        AccessibilityGuard.tryEnable(context)
        BunkerWatchdogService.start(context)
    }
}
