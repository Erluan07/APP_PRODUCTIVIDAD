package com.kronos.focus.domain.time

import java.time.Instant
import java.time.ZoneId

/** Un tramo de tiempo contado dentro de un día concreto (día = epoch-day local). */
data class DaySpan(val epochDay: Long, val ms: Long)

/** Lógica pura del contador de tiempos: reparte un tramo [startMs, endMs) entre los días que cruza (medianoche local). */
object TimeSpans {
    fun split(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<DaySpan> {
        if (endMs <= startMs) return emptyList()
        val out = ArrayList<DaySpan>()
        var cur = startMs
        while (cur < endMs) {
            val day = Instant.ofEpochMilli(cur).atZone(zone).toLocalDate()
            val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(endMs, nextMidnight)
            out.add(DaySpan(day.toEpochDay(), end - cur))
            cur = end
        }
        return out
    }

    /** "3:05" (horas:minutos) o, con [seconds], "3:05:09". */
    fun fmt(ms: Long, seconds: Boolean = false): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (seconds) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(h, m)
    }
}
