package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.alarm.AlarmEntry
import com.kronos.focus.domain.alarm.AlarmTimes
import org.json.JSONArray
import org.json.JSONObject

/** Alarmas del usuario (hora, días, activada). Ya no se crea ninguna por defecto. */
class AlarmConfigStore private constructor(private val prefs: SharedPreferences) {

    fun all(): List<AlarmEntry> {
        dropLegacyDefault()
        return runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                AlarmEntry(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getInt("d"), o.getBoolean("on"), o.optString("l", ""))
            }
        }.getOrDefault(emptyList())
    }

    /** Versiones anteriores sembraban una alarma "Predeterminada" 07:00; se elimina una sola vez. */
    @Synchronized
    private fun dropLegacyDefault() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val keep = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val legacy = o.optString("l") == "Predeterminada" && o.getInt("h") == 7 && o.getInt("m") == 0
                if (!legacy) keep.put(o)
            }
            prefs.edit().putString(KEY, keep.toString()).apply()
        }
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
        private const val KEY_MIGRATED = "legacy_default_removed"
        @Volatile private var instance: AlarmConfigStore? = null
        fun get(context: Context): AlarmConfigStore = instance ?: synchronized(this) {
            instance ?: AlarmConfigStore(
                context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("alarm_config", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
