package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.time.TimeSpans
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class TimeCategory(val id: Int, val name: String, val goalMin: Int)

/**
 * Contador de tiempos por categoría (trabajo, entretenimiento, gimnasio…): empiezas, paras y se acumula por día,
 * con una meta diaria. Solo uno corre a la vez. Es contabilidad, no una regla: no bloquea nada.
 */
class TimeTrackerStore private constructor(private val prefs: SharedPreferences) {

    fun categories(): List<TimeCategory> {
        if (!prefs.getBoolean("seeded", false)) {
            // Primera vez: las tres del ejemplo (editables).
            saveCats(listOf(TimeCategory(1, "Trabajo", 8 * 60), TimeCategory(2, "Entretenimiento", 2 * 60), TimeCategory(3, "Gimnasio", 2 * 60)))
            prefs.edit().putBoolean("seeded", true).apply()
        }
        return runCatching {
            val arr = JSONArray(prefs.getString("cats", "[]"))
            (0 until arr.length()).map { val o = arr.getJSONObject(it); TimeCategory(o.getInt("i"), o.getString("n"), o.getInt("g")) }
        }.getOrDefault(emptyList())
    }

    private fun saveCats(list: List<TimeCategory>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("i", it.id).put("n", it.name).put("g", it.goalMin)) }
        prefs.edit().putString("cats", arr.toString()).apply()
    }

    fun addCategory(name: String, goalMin: Int): Boolean {
        val n = name.trim()
        val cur = categories()
        if (n.isEmpty() || goalMin <= 0 || cur.any { it.name.equals(n, true) }) return false
        saveCats(cur + TimeCategory((cur.maxOfOrNull { it.id } ?: 0) + 1, n, goalMin))
        return true
    }

    fun removeCategory(id: Int, now: Long = System.currentTimeMillis()) {
        if (running()?.first == id) prefs.edit().remove("run_id").remove("run_start").apply()
        saveCats(categories().filterNot { it.id == id })
    }

    /** (categoría, inicio) de la que está corriendo, o null. */
    fun running(): Pair<Int, Long>? {
        val id = prefs.getInt("run_id", -1)
        return if (id < 0) null else id to prefs.getLong("run_start", 0L)
    }

    /** Empieza [id]; si había otra corriendo, la para primero y guarda su tramo. */
    fun start(id: Int, now: Long = System.currentTimeMillis()) {
        stop(now)
        prefs.edit().putInt("run_id", id).putLong("run_start", now).apply()
    }

    fun stop(now: Long = System.currentTimeMillis()) {
        val (id, start) = running() ?: return
        addSpan(id, start, now)
        prefs.edit().remove("run_id").remove("run_start").apply()
    }

    private fun days(): JSONObject = runCatching { JSONObject(prefs.getString("days", "{}")) }.getOrDefault(JSONObject())

    private fun addSpan(id: Int, startMs: Long, endMs: Long) {
        val d = days()
        TimeSpans.split(startMs, endMs).forEach { sp ->
            val day = d.optJSONObject(sp.epochDay.toString()) ?: JSONObject().also { d.put(sp.epochDay.toString(), it) }
            day.put(id.toString(), day.optLong(id.toString(), 0L) + sp.ms)
        }
        prefs.edit().putString("days", d.toString()).apply()
    }

    private fun storedMs(id: Int, epochDay: Long): Long = days().optJSONObject(epochDay.toString())?.optLong(id.toString(), 0L) ?: 0L

    /** Tiempo acumulado hoy en [id] (incluye el tramo que está corriendo). */
    fun todayMs(id: Int, now: Long = System.currentTimeMillis()): Long = daysMs(id, now, 1)

    /** Tiempo acumulado en los últimos [n] días contando hoy. */
    fun daysMs(id: Int, now: Long, n: Int): Long {
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        var total = (0 until n).sumOf { storedMs(id, today - it) }
        running()?.let { (rid, start) ->
            if (rid == id) total += TimeSpans.split(start, now).filter { it.epochDay > today - n }.sumOf { it.ms }
        }
        return total
    }

    companion object {
        @Volatile private var instance: TimeTrackerStore? = null
        fun get(context: Context): TimeTrackerStore = instance ?: synchronized(this) {
            instance ?: TimeTrackerStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("time_tracker", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
