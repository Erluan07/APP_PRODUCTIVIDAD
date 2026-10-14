package com.kronos.focus.domain.agenda

/** Tarea de "Mis tareas". Con [startMin]/[endMin] (minutos desde medianoche) es fija; sin ellos, flexible. */
data class TaskTemplate(val name: String, val startMin: Int? = null, val endMin: Int? = null, val must: Boolean = false, val verify: Boolean = false) {
    /** Obligatoria: solo hora de inicio; bloquea el teléfono desde esa hora hasta subir la evidencia. */
    val isMust get() = must && startMin != null
    val isTimed get() = !must && startMin != null && endMin != null
    fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    fun label() = if (isMust) "Obligatoria desde ${hm(startMin!!)}  $name" else if (isTimed) "${hm(startMin!!)}–${hm(endMin!!)}  $name" else "$name (sin hora)"
}

/** Tarea del día sin hora ni duración: se hace cuando el usuario decide iniciar un búnker para ella. */
data class FlexTask(val name: String, val done: Boolean = false)
