package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.rules.AppRulesConfig
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/** Fase del Pomodoro: [focus] = enfoque (true) o descanso (false); [remainingSec] de la fase. */
data class PomodoroPhase(val focus: Boolean, val remainingSec: Long)

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

    @Volatile private var pomoStartMs: Long = prefs.getLong(KEY_POMO_START, 0L)
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

    /**
     * Inicia/alarga un bloque de [minutes] (tope por sesión y tope total). Con [pomodoro] el búnker se
     * divide en ciclos de 50 min de enfoque + 10 de descanso (solo si no había ya un búnker manual en curso).
     */
    fun startSession(minutes: Long, pomodoro: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now >= endsAtMs) setPomodoro(if (pomodoro) now else 0L)
        val m = minutes.coerceAtMost(AppRulesConfig.MAX_SINGLE_BUNKER_MIN)
        extendUntil(now + m * 60_000L)
    }

    private fun setPomodoro(startMs: Long) {
        pomoStartMs = startMs
        prefs.edit().putLong(KEY_POMO_START, startMs).putInt(KEY_POMO_LAST, -1).apply()
    }

    private fun pomoCycleMs() = (AppRulesConfig.POMODORO_FOCUS_MIN + AppRulesConfig.POMODORO_BREAK_MIN) * 60_000L

    /** Fase actual del Pomodoro (null si no hay Pomodoro en curso). */
    fun pomodoroPhase(now: Long = System.currentTimeMillis()): PomodoroPhase? {
        if (pomoStartMs == 0L || now < pomoStartMs || now >= endsAtMs) return null
        val pos = (now - pomoStartMs) % pomoCycleMs()
        val focusMs = AppRulesConfig.POMODORO_FOCUS_MIN * 60_000L
        return if (pos < focusMs) PomodoroPhase(true, (focusMs - pos) / 1000)
        else PomodoroPhase(false, (pomoCycleMs() - pos) / 1000)
    }

    /** Devuelve la fase a anunciar UNA sola vez cuando cambia (enfoque ⇄ descanso); null si no hay cambio. */
    fun pomodoroTransition(now: Long = System.currentTimeMillis()): PomodoroPhase? {
        val phase = pomodoroPhase(now) ?: return null
        val idx = (((now - pomoStartMs) / pomoCycleMs()) * 2 + (if (phase.focus) 0 else 1)).toInt()
        if (idx == prefs.getInt(KEY_POMO_LAST, -1)) return null
        prefs.edit().putInt(KEY_POMO_LAST, idx).apply()
        return if (idx == 0) null else phase
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
        setPomodoro(0L)
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
        private const val KEY_POMO_START = "pomo_start"
        private const val KEY_POMO_LAST = "pomo_last"
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
