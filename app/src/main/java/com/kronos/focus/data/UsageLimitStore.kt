package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.limits.LimitBlock
import com.kronos.focus.domain.limits.UsageLimits
import org.json.JSONObject
import java.time.LocalDate

/**
 * Límites de uso por app: máximo diario + corte tras uso continuo. Los límites solo se pueden agregar o endurecer
 * (no hay forma de quitarlos ni aflojarlos). El uso del día y los bloqueos temporales viven aquí.
 */
class UsageLimitStore private constructor(private val prefs: SharedPreferences) {
    private val limits = HashMap<String, Int>()
    private val used = HashMap<String, Long>()
    private val cooldown = HashMap<String, Long>()
    private var day = LocalDate.now().toEpochDay()
    private var lastPersist = 0L

    init {
        runCatching {
            val o = JSONObject(prefs.getString(KEY_LIMITS, "{}") ?: "{}")
            o.keys().forEach { limits[it] = o.getInt(it) }
            val u = JSONObject(prefs.getString(KEY_USED, "{}") ?: "{}")
            if (u.optLong("day", -1) == day) u.optJSONObject("ms")?.let { m -> m.keys().forEach { used[it] = m.getLong(it) } }
            val c = JSONObject(prefs.getString(KEY_COOLDOWN, "{}") ?: "{}")
            c.keys().forEach { cooldown[it] = c.getLong(it) }
        }
    }

    @Synchronized fun hasAny(): Boolean = limits.isNotEmpty()
    @Synchronized fun isLimited(pkg: String): Boolean = limits.containsKey(pkg)
    @Synchronized fun limitMin(pkg: String): Int? = limits[pkg]
    @Synchronized fun all(): Map<String, Int> = HashMap(limits)

    /** Pone o endurece un límite. Devuelve false si intenta aflojarlo. */
    @Synchronized fun setLimit(pkg: String, minutes: Int): Boolean {
        if (!UsageLimits.canSet(limits[pkg], minutes)) return false
        limits[pkg] = minutes
        val o = JSONObject(); limits.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_LIMITS, o.toString()).apply()
        return true
    }

    @Synchronized private fun rollDay() {
        val today = LocalDate.now().toEpochDay()
        if (today != day) { day = today; used.clear() }
    }

    @Synchronized fun usedTodayMs(pkg: String): Long { rollDay(); return used[pkg] ?: 0L }

    /** Suma uso. Persiste como mucho cada ~20 s para no escribir de más. */
    @Synchronized fun addUsage(pkg: String, ms: Long, now: Long) {
        rollDay()
        used[pkg] = (used[pkg] ?: 0L) + ms
        if (now - lastPersist >= 20_000L) persistUsage(now)
    }

    @Synchronized fun persistUsage(now: Long = System.currentTimeMillis()) {
        lastPersist = now
        val m = JSONObject(); used.forEach { (k, v) -> m.put(k, v) }
        prefs.edit().putString(KEY_USED, JSONObject().put("day", day).put("ms", m).toString()).apply()
    }

    @Synchronized fun startCooldown(pkg: String, untilMs: Long) {
        cooldown[pkg] = untilMs
        val o = JSONObject(); cooldown.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_COOLDOWN, o.toString()).apply()
    }

    @Synchronized fun blockOf(pkg: String, now: Long): LimitBlock? {
        val lim = limits[pkg] ?: return null
        rollDay()
        return UsageLimits.blockFor(used[pkg] ?: 0L, lim, cooldown[pkg] ?: 0L, now)
    }

    companion object {
        private const val KEY_LIMITS = "limits"
        private const val KEY_USED = "used"
        private const val KEY_COOLDOWN = "cooldown"
        @Volatile private var instance: UsageLimitStore? = null
        fun get(context: Context): UsageLimitStore = instance ?: synchronized(this) {
            instance ?: UsageLimitStore(
                context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("usage_limits", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
