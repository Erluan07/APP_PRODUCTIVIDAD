package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.rules.AppRulesConfig
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/**
 * Estado del Modo Búnker en almacenamiento *device-protected* (accesible en Direct Boot).
 * No expone cancelación: solo se puede extender (con tope). [debugClear] solo lo llama la UI en builds debug.
 */
class BunkerStateStore private constructor(
    private val prefs: SharedPreferences,
    private val agenda: AgendaStore,
    private val stats: StatsStore,
) {

    @Volatile private var endsAtMs: Long = prefs.getLong(KEY_ENDS_AT, 0L)

    @Volatile private var sleepStartMs: Long = prefs.getLong(KEY_SLEEP_START, 0L)
    @Volatile private var sleepEndMs: Long = prefs.getLong(KEY_SLEEP_END, 0L)

    private fun epochDay(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /** Modo Dormir: lo activa el usuario ("Me voy a dormir") y dura hasta su próxima alarma. */
    fun isSleepMode(now: Long = System.currentTimeMillis()): Boolean = now in sleepStartMs until sleepEndMs

    fun startSleepMode(endMs: Long, now: Long = System.currentTimeMillis()) {
        sleepStartMs = now; sleepEndMs = endMs
        prefs.edit().putLong(KEY_SLEEP_START, sleepStartMs).putLong(KEY_SLEEP_END, sleepEndMs).apply()
    }

    /** Termina el Modo Dormir (cuando suena la alarma). */
    fun endSleep() {
        sleepStartMs = 0L; sleepEndMs = 0L
        prefs.edit().putLong(KEY_SLEEP_START, 0L).putLong(KEY_SLEEP_END, 0L).apply()
    }

    /** Activo si hay un búnker manual, un bloque de la agenda (salvo en la pausa fija) o el Modo Dormir. */
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        ((now < endsAtMs || agenda.activeBlockAt(now) != null) && !inFreeWindow(now)) || isSleepMode(now)

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        maxOf(endsAtMs - now, (agenda.activeBlockAt(now)?.endMs ?: 0L) - now, if (isSleepMode(now)) sleepEndMs - now else 0L, 0L)

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

    /**
     * Penalización de arranque: se aplica UNA sola vez por arranque (LOCKED_BOOT_COMPLETED y
     * BOOT_COMPLETED llegan los dos). [bootCount] viene de Settings.Global.BOOT_COUNT.
     */
    fun applyBootPenaltyOnce(bootCount: Int, minutes: Long) {
        if (bootCount == prefs.getInt(KEY_PENALTY_BOOT, -2)) return
        prefs.edit().putInt(KEY_PENALTY_BOOT, bootCount).apply()
        if (isActive()) applyPenalty(minutes) else startSession(minutes)
    }

    /** SOLO builds debug: salida de emergencia durante las pruebas. */
    fun debugClear() {
        endsAtMs = 0L
        prefs.edit().putLong(KEY_ENDS_AT, 0L).apply()
        endSleep()
        agenda.clear()
    }

    // ---- Diagnóstico / registro de intentos ----
    fun recordSeen(pkg: String, allowed: Boolean) {
        val line = "${System.currentTimeMillis()}|${if (allowed) 1 else 0}|$pkg"
        val list = (listOf(line) + recent().map { "${it.first}|${if (it.second) 1 else 0}|${it.third}" }).take(15)
        val e = prefs.edit().putString(KEY_RECENT, list.joinToString("\n"))
        if (!allowed) { e.putInt(KEY_BLOCKED, blockedCount() + 1); stats.recordBlocked(epochDay(System.currentTimeMillis())) }
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
        private const val KEY_SLEEP_START = "sleep_start"
        private const val KEY_SLEEP_END = "sleep_end"
        private const val KEY_PENALTY_BOOT = "penalty_boot_count"
        @Volatile private var instance: BunkerStateStore? = null

        fun get(context: Context): BunkerStateStore = instance ?: synchronized(this) {
            instance ?: run {
                val dp = context.applicationContext.createDeviceProtectedStorageContext()
                BunkerStateStore(dp.getSharedPreferences("bunker_state", Context.MODE_PRIVATE), AgendaStore.get(context), StatsStore.get(context))
            }.also { instance = it }
        }
    }
}
