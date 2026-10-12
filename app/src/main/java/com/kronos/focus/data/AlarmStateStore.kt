package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences

enum class AlarmPhase { IDLE, RINGING, RITUAL, CHECK_WAIT, CHECK_ACTIVE }

/** Estado de la alarma en almacenamiento device-protected (sobrevive a reinicios y Direct Boot). */
class AlarmStateStore private constructor(private val prefs: SharedPreferences) {

    var phase: AlarmPhase
        get() = runCatching { AlarmPhase.valueOf(prefs.getString("phase", "IDLE")!!) }.getOrDefault(AlarmPhase.IDLE)
        set(v) { prefs.edit().putString("phase", v.name).apply() }

    var checkAtMs: Long
        get() = prefs.getLong("check_at", 0L)
        set(v) { prefs.edit().putLong("check_at", v).apply() }

    var checkDeadlineMs: Long
        get() = prefs.getLong("check_deadline", 0L)
        set(v) { prefs.edit().putLong("check_deadline", v).apply() }

    /** Límite para completar el ritual de agenda (Misión 2). */
    var ritualDeadlineMs: Long
        get() = prefs.getLong("ritual_deadline", 0L)
        set(v) { prefs.edit().putLong("ritual_deadline", v).apply() }

    /** Hasta cuándo está silenciada la alarma (botón "voy a escanear"). */
    var muteUntilMs: Long
        get() = prefs.getLong("mute_until", 0L)
        set(v) { prefs.edit().putLong("mute_until", v).apply() }

    var muteUses: Int
        get() = prefs.getInt("mute_uses", 0)
        set(v) { prefs.edit().putInt("mute_uses", v).apply() }

    /** Día (epoch-day) en el que ya sonó la alarma real, para no repetirla. */
    var lastRingDay: Long
        get() = prefs.getLong("last_ring_day", -1L)
        set(v) { prefs.edit().putLong("last_ring_day", v).apply() }

    /** La pantalla de alarma debe estar al frente (suena o hay cuenta atrás del Wake-Up Check). */
    fun uiRequired(): Boolean = phase.let { it == AlarmPhase.RINGING || it == AlarmPhase.RITUAL || it == AlarmPhase.CHECK_ACTIVE }

    companion object {
        @Volatile private var instance: AlarmStateStore? = null
        fun get(context: Context): AlarmStateStore = instance ?: synchronized(this) {
            instance ?: AlarmStateStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
