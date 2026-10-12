package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.rules.AppRulesConfig
import java.util.Calendar

/**
 * Estado del Modo Búnker en almacenamiento *device-protected* (accesible en Direct Boot).
 * No expone cancelación: solo se puede extender (con tope). [debugClear] solo lo llama la UI en builds debug.
 */
class BunkerStateStore private constructor(private val prefs: SharedPreferences, private val agenda: AgendaStore) {

    @Volatile private var endsAtMs: Long = prefs.getLong(KEY_ENDS_AT, 0L)

    /** Activo si hay un búnker manual en curso o un bloque de la agenda del día, salvo en la pausa fija. */
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        (now < endsAtMs || agenda.activeBlockAt(now) != null) && !inFreeWindow(now)

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        maxOf(endsAtMs - now, (agenda.activeBlockAt(now)?.endMs ?: 0L) - now, 0L)

    fun inFreeWindow(now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return AppRulesConfig.FREE_WINDOWS.any { m >= it.first && m < it.second }
    }

    /** Inicia/alarga un bloque de [minutes] (tope por sesión y tope total). */
    fun startSession(minutes: Long) {
        val m = minutes.coerceAtMost(AppRulesConfig.MAX_SINGLE_BUNKER_MIN)
        extendUntil(System.currentTimeMillis() + m * 60_000L)
    }

    /** Solo alarga, nunca acorta; respeta el tope total. */
    fun extendUntil(epochMs: Long) {
        val cap = System.currentTimeMillis() + AppRulesConfig.MAX_TOTAL_BUNKER_MIN * 60_000L
        val target = epochMs.coerceAtMost(cap)
        if (target > endsAtMs) {
            endsAtMs = target
            prefs.edit().putLong(KEY_ENDS_AT, target).apply()
        }
    }

    /** Penalización por reinicio forzado (solo builds release; ver BootReceiver). */
    fun applyPenalty(minutes: Long) = extendUntil(endsAtMs + minutes * 60_000L)

    /** SOLO builds debug: salida de emergencia durante las pruebas. */
    fun debugClear() {
        endsAtMs = 0L
        prefs.edit().putLong(KEY_ENDS_AT, 0L).apply()
        agenda.clear()
    }

    // ---- Diagnóstico / registro de intentos ----
    fun recordSeen(pkg: String, allowed: Boolean) {
        val line = "${System.currentTimeMillis()}|${if (allowed) 1 else 0}|$pkg"
        val list = (listOf(line) + recent().map { "${it.first}|${if (it.second) 1 else 0}|${it.third}" }).take(15)
        val e = prefs.edit().putString(KEY_RECENT, list.joinToString("\n"))
        if (!allowed) e.putInt(KEY_BLOCKED, blockedCount() + 1)
        e.apply()
    }

    fun recent(): List<Triple<Long, Boolean, String>> =
        (prefs.getString(KEY_RECENT, "") ?: "").lines().mapNotNull {
            val p = it.split('|', limit = 3)
            if (p.size == 3) Triple(p[0].toLongOrNull() ?: 0L, p[1] == "1", p[2]) else null
        }

    fun blockedCount(): Int = prefs.getInt(KEY_BLOCKED, 0)
    fun tamperCount(): Int = prefs.getInt(KEY_TAMPER, 0)
    fun recordTamper() { prefs.edit().putInt(KEY_TAMPER, tamperCount() + 1).apply() }

    companion object {
        private const val KEY_ENDS_AT = "bunker_ends_at"
        private const val KEY_RECENT = "recent_packages"
        private const val KEY_BLOCKED = "blocked_count"
        private const val KEY_TAMPER = "tamper_count"
        @Volatile private var instance: BunkerStateStore? = null

        fun get(context: Context): BunkerStateStore = instance ?: synchronized(this) {
            instance ?: run {
                val dp = context.applicationContext.createDeviceProtectedStorageContext()
                BunkerStateStore(dp.getSharedPreferences("bunker_state", Context.MODE_PRIVATE), AgendaStore.get(context))
            }.also { instance = it }
        }
    }
}
