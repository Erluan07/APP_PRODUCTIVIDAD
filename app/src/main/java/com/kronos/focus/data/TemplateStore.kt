package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.agenda.TaskTemplate
import org.json.JSONArray
import org.json.JSONObject

/** "Mis tareas": lista editable de tareas típicas / importadas (datos, no reglas). */
class TemplateStore private constructor(private val prefs: SharedPreferences) {

    fun all(): List<TaskTemplate> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            TaskTemplate(o.getString("n"), o.optInt("s", -1).takeIf { v -> v >= 0 }, o.optInt("e", -1).takeIf { v -> v >= 0 })
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<TaskTemplate>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("s", it.startMin ?: -1).put("e", it.endMin ?: -1)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Añade o reemplaza (por nombre, sin distinguir mayúsculas). */
    fun upsert(t: TaskTemplate) = save(all().filterNot { it.name.equals(t.name, true) } + t)

    fun remove(name: String) = save(all().filterNot { it.name.equals(name, true) })

    /** Importa nombres como tareas flexibles sin pisar las que ya existen. Devuelve cuántas se añadieron. */
    fun importNames(names: List<String>): Int {
        val cur = all()
        val fresh = names.filter { n -> cur.none { it.name.equals(n, true) } }.map { TaskTemplate(it) }
        if (fresh.isNotEmpty()) save(cur + fresh)
        return fresh.size
    }

    companion object {
        private const val KEY = "templates"
        @Volatile private var instance: TemplateStore? = null
        fun get(context: Context): TemplateStore = instance ?: synchronized(this) {
            instance ?: TemplateStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("templates", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
