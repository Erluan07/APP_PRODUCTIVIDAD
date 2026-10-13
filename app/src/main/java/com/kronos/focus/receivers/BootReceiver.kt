package com.kronos.focus.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.kronos.focus.BuildConfig
import com.kronos.focus.admin.DeviceOwnerHardening
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.services.AlarmFlow
import com.kronos.focus.services.BunkerWatchdogService

/**
 * Boot Trap. Tras un arranque: reprograma alarmas y agenda; si había un búnker o una alarma
 * pendiente, restaura el bloqueo y (solo en builds release) aplica +60 min de penalización una vez.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmFlow.restoreAfterBoot(context)
        val store = BunkerStateStore.get(context)
        val pendingAlarm = AlarmStateStore.get(context).uiRequired()
        if (store.isActive() || pendingAlarm) {
            if (!BuildConfig.TEST_BUILD) {
                val boots = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                store.applyBootPenaltyOnce(boots, AppRulesConfig.BOOT_TRAP_PENALTY_MIN)
            }
            AccessibilityGuard.tryEnable(context)
            BunkerWatchdogService.start(context)
        }
        DeviceOwnerHardening.sync(context, store.isActive())
    }
}
