package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Estado del Modo Búnker en almacenamiento *device-protected* (accesible en Direct Boot).
 * Fase 3 lo alimentará desde los bloques de agenda; Fase 5 añade la penalización de arranque.
 * No expone ninguna operación de cancelación: solo se puede extender.
 */
class BunkerStateStore private constructor(private val prefs: SharedPreferences) {

    @Volatile private var endsAtMs: Long = prefs.getLong(KEY_ENDS_AT, 0L)

    fun isActive(now: Long = System.currentTimeMillis()): Boolean = now < endsAtMs

    /** Solo permite alargar el bloqueo, nunca acortarlo. */
    fun extendUntil(epochMs: Long) {
        if (epochMs > endsAtMs) {
            endsAtMs = epochMs
            prefs.edit().putLong(KEY_ENDS_AT, epochMs).apply()
        }
    }

    fun remainingMs(now: Long = System.currentTimeMillis()): Long = (endsAtMs - now).coerceAtLeast(0)

    companion object {
        private const val KEY_ENDS_AT = "bunker_ends_at"
        @Volatile private var instance: BunkerStateStore? = null

        fun get(context: Context): BunkerStateStore = instance ?: synchronized(this) {
            instance ?: run {
                val dp = context.applicationContext.createDeviceProtectedStorageContext()
                BunkerStateStore(dp.getSharedPreferences("bunker_state", Context.MODE_PRIVATE))
            }.also { instance = it }
        }
    }
}
