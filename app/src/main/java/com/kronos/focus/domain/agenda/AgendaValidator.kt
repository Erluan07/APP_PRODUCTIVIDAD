package com.kronos.focus.domain.agenda

import com.kronos.focus.domain.rules.AppRulesConfig
import java.util.Calendar

data class AgendaBlock(val name: String, val startMs: Long, val endMs: Long)

/** Reglas puras del Compromiso Diario (sin Android, fáciles de probar). */
object AgendaValidator {

    /** "8", "08:30", "0830", "830", "8.30" -> minutos desde medianoche, o null si no es válido. */
    fun parseHm(text: String): Int? {
        val m = Regex("^(\\d{1,2})(?:[:.]?(\\d{2}))?$").matchEntire(text.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    fun todayAt(minutes: Int, now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun sameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }

    /** Devuelve un mensaje de error o null si la agenda es válida. */
    fun validate(blocks: List<AgendaBlock>, now: Long): String? {
        if (blocks.size < AppRulesConfig.AGENDA_MIN_BLOCKS) return "Agrega al menos ${AppRulesConfig.AGENDA_MIN_BLOCKS} bloque."
        val sorted = blocks.sortedBy { it.startMs }
        var total = 0L
        for ((i, b) in sorted.withIndex()) {
            if (b.name.isBlank()) return "Todo bloque necesita un nombre."
            val min = (b.endMs - b.startMs) / 60_000
            if (min <= 0) return "\"${b.name}\": el fin debe ser posterior al inicio."
            if (min < AppRulesConfig.MIN_BLOCK_MIN) return "\"${b.name}\" dura menos de ${AppRulesConfig.MIN_BLOCK_MIN} min."
            if (min > AppRulesConfig.MAX_SINGLE_BUNKER_MIN) return "\"${b.name}\" supera el máximo de ${AppRulesConfig.MAX_SINGLE_BUNKER_MIN} min por bloque."
            if (!sameDay(b.startMs, b.endMs)) return "\"${b.name}\" debe terminar el mismo día."
            if (b.endMs <= now + 5 * 60_000L) return "\"${b.name}\" ya terminó o termina en menos de 5 min."
            if (i > 0 && b.startMs < sorted[i - 1].endMs) return "\"${sorted[i - 1].name}\" y \"${b.name}\" se solapan."
            total += min
        }
        if (total > AppRulesConfig.MAX_TOTAL_BUNKER_MIN) return "Total de búnker del día ($total min) supera el máximo (${AppRulesConfig.MAX_TOTAL_BUNKER_MIN} min)."
        return null
    }
}
