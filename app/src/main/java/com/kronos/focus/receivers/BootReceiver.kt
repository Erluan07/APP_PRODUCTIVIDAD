package com.kronos.focus.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig

/**
 * Boot Trap (esqueleto Fase 5). Si había un bloque activo al reiniciar, aplica penalización.
 * TODO Fase 5: distinguir reinicio con bloque pendiente (marca en prefs device-protected)
 * y levantar el servicio de bloqueo/alarma.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = BunkerStateStore.get(context)
        if (store.isActive()) {
            store.extendUntil(System.currentTimeMillis() + store.remainingMs() +
                AppRulesConfig.BOOT_TRAP_PENALTY_MIN * 60_000L)
        }
    }
}
