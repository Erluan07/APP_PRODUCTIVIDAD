package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.alarm.AlarmEntry
import com.kronos.focus.domain.alarm.AlarmTimes
import org.json.JSONArray
import org.json.JSONObject

/** Alarmas del usuario (hora, días, activada). La primera vez se crea una de 07:00 todos los días. */
class AlarmConfigStore private constructor(private val prefs: SharedPreferences) {

    fun all(): List<AlarmEntry> {
        if (!prefs.contains(KEY)) { save(listOf(AlarmEntry(1, 7, 0, AlarmTimes.ALL_DAYS, true, "Predeterminada"))) }
        return runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                AlarmEntry(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getInt("d"), o.getBoolean("on"), o.optString("l", ""))
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<AlarmEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("d", it.days).put("on", it.enabled).put("l", it.label)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun upsert(a: AlarmEntry): AlarmEntry {
        val cur = all()
        val withId = if (a.id == 0) a.copy(id = (cur.maxOfOrNull { it.id } ?: 0) + 1) else a
        save(cur.filterNot { it.id == withId.id } + withId)
        return withId
    }

    @Synchronized
    fun delete(id: Int) = save(all().filterNot { it.id == id })

    companion object {
        private const val KEY = "alarms"
        @Volatile private var instance: AlarmConfigStore? = null
        fun get(context: Context): AlarmConfigStore = instance ?: synchronized(this) {
            instance ?: AlarmConfigStore(
                context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("alarm_config", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
