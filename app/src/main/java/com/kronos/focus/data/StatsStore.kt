package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.stats.DayStat
import org.json.JSONObject

/** Contadores por día (tareas, hechas con foto, intentos bloqueados) para rachas y estadísticas. */
class StatsStore private constructor(private val prefs: SharedPreferences) {

    private fun root(): JSONObject = runCatching { JSONObject(prefs.getString(KEY, "{}")) }.getOrDefault(JSONObject())

    fun all(): Map<Long, DayStat> {
        val o = root()
        return o.keys().asSequence().associate { k ->
            val a = o.getJSONArray(k)
            k.toLong() to DayStat(a.getInt(0), a.getInt(1), a.getInt(2))
        }
    }

    @Synchronized
    private fun update(day: Long, f: (DayStat) -> DayStat) {
        val o = root()
        val cur = o.optJSONArray(day.toString())?.let { DayStat(it.getInt(0), it.getInt(1), it.getInt(2)) } ?: DayStat()
        val n = f(cur)
        o.put(day.toString(), org.json.JSONArray().put(n.tasks).put(n.done).put(n.blocked))
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    fun recordCommit(day: Long, tasks: Int) = update(day) { it.copy(tasks = maxOf(it.tasks, tasks)) }
    fun addTasks(day: Long, n: Int = 1) = update(day) { it.copy(tasks = it.tasks + n) }
    fun recordDone(day: Long) = update(day) { it.copy(done = it.done + 1) }
    fun recordBlocked(day: Long) = update(day) { it.copy(blocked = it.blocked + 1) }

    companion object {
        private const val KEY = "days"
        @Volatile private var instance: StatsStore? = null
        fun get(context: Context): StatsStore = instance ?: synchronized(this) {
            instance ?: StatsStore(
                context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("stats", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
