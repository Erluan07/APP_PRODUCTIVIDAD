package com.kronos.focus.domain.agenda

/**
 * Tarea obligatoria: tiene hora de inicio pero NO de fin. Desde [startMs] el teléfono queda bloqueado (como el Modo Búnker)
 * hasta que se marca como hecha subiendo una foto de evidencia. No se puede quitar ni cancelar.
 */
data class MustTask(val name: String, val startMs: Long, val done: Boolean = false)

object MustRules {
    /** La tarea obligatoria que está bloqueando ahora: pendiente y ya iniciada. */
    fun activeAt(tasks: List<MustTask>, now: Long): MustTask? = tasks.firstOrNull { !it.done && it.startMs <= now }

    /** Error al agregar o null. Solo se puede agregar, y no repetir el nombre de una pendiente. */
    fun validateAdd(existing: List<MustTask>, name: String): String? = when {
        name.isBlank() -> "Escribe el nombre."
        existing.any { !it.done && it.name.equals(name.trim(), ignoreCase = true) } -> "Ya tienes una tarea obligatoria pendiente con ese nombre."
        else -> null
    }
}
