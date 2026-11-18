package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Rutina de la mañana: pasos en orden, cada uno con foto de evidencia.
 * "Armada" = al irte a dormir dijiste que sí; se consume al escanear el código de la alarma.
 * El progreso es del día (si suena otra vez el mismo día, se conservan los pasos ya hechos).
 */
class RoutineStore private constructor(private val prefs: SharedPreferences) {

    fun steps(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("steps", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveSteps(list: List<String>) {
        prefs.edit().putString("steps", JSONArray(list).toString()).apply()
    }

    /** Solo se edita con la rutina libre: ni armada para mañana ni en curso. */
    fun editable(ctx: Context): Boolean =
        !armed && AlarmStateStore.get(ctx).phase != AlarmPhase.ROUTINE

    fun add(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || steps().any { it.equals(n, true) }) return false
        saveSteps(steps() + n)
        return true
    }

    fun remove(name: String) = saveSteps(steps().filterNot { it == name })

    fun move(name: String, delta: Int) {
        val l = steps().toMutableList()
        val i = l.indexOf(name)
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        saveSteps(l)
    }

    var armed: Boolean
        get() = prefs.getBoolean("armed", false)
        set(v) { prefs.edit().putBoolean("armed", v).apply() }

    private var day: Long
        get() = prefs.getLong("day", -1L)
        set(v) { prefs.edit().putLong("day", v).apply() }

    private fun doneList(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("done", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    /** Empieza (o retoma) la rutina del día [epochDay]. */
    fun begin(epochDay: Long) {
        if (day != epochDay) { day = epochDay; prefs.edit().putString("done", "[]").apply() }
        armed = false
    }

    /** Hoy ya empezó la rutina y faltan pasos (p. ej. la alarma volvió a sonar a medias). */
    fun inProgress(epochDay: Long) = day == epochDay && !allDone()

    /** SOLO para salir del Modo prueba. */
    fun reset() {
        prefs.edit().putBoolean("armed", false).putLong("day", -1L).putString("done", "[]").apply()
    }

    fun isDone(step: String) = step in doneList()

    /** Paso actual: el primero de la lista sin evidencia. */
    fun current(): String? = steps().firstOrNull { it !in doneList() }

    fun completeStep(step: String) {
        if (step !in doneList()) prefs.edit().putString("done", JSONArray(doneList() + step).toString()).apply()
    }

    fun allDone(): Boolean = steps().all { it in doneList() }

    companion object {
        @Volatile private var instance: RoutineStore? = null
        fun get(context: Context): RoutineStore = instance ?: synchronized(this) {
            instance ?: RoutineStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("routine", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
