package com.kronos.focus.domain.stats

/** Un día cuenta como cumplido si tenía tareas y se hicieron todas (con su foto de evidencia). */
data class DayStat(val tasks: Int = 0, val done: Int = 0, val blocked: Int = 0) {
    val complete get() = tasks > 0 && done >= tasks
}

object StreakCalc {
    /** Racha actual: días seguidos cumplidos hasta hoy (si hoy aún no está cumplido, hasta ayer). */
    fun current(days: Map<Long, DayStat>, today: Long): Int {
        var d = if (days[today]?.complete == true) today else today - 1
        var n = 0
        while (days[d]?.complete == true) { n++; d-- }
        return n
    }

    fun best(days: Map<Long, DayStat>): Int {
        var best = 0; var run = 0; var prev: Long? = null
        for (d in days.keys.sorted()) {
            if (days[d]?.complete != true) { run = 0; prev = null; continue }
            run = if (prev != null && d == prev + 1) run + 1 else 1
            prev = d
            if (run > best) best = run
        }
        return best
    }
}
