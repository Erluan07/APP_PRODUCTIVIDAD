package com.kronos.focus.domain.alarm

import java.time.LocalDate
import java.util.Calendar

/** Una alarma configurada por el usuario. [days] es una máscara: bit 0 = lunes ... bit 6 = domingo. */
data class AlarmEntry(val id: Int, val hour: Int, val minute: Int, val days: Int, val enabled: Boolean, val label: String) {
    val minutesOfDay get() = hour * 60 + minute
    fun hm() = "%02d:%02d".format(hour, minute)
}

/** Lógica pura de horarios (sin Android): próxima ocurrencia y bloqueo de edición. */
object AlarmTimes {
    const val ALL_DAYS = 0b1111111
    private val NAMES = listOf("L", "M", "X", "J", "V", "S", "D")

    /** Bit del día según Calendar.DAY_OF_WEEK (domingo = 1). Lunes = bit 0 ... domingo = bit 6. */
    fun bit(dayOfWeek: Int): Int = 1 shl ((dayOfWeek + 5) % 7)

    fun daysText(days: Int): String = when {
        days == ALL_DAYS -> "Todos los días"
        days == 0b0011111 -> "Lunes a viernes"
        days == 0b1100000 -> "Fin de semana"
        days == 0 -> "Ningún día"
        else -> NAMES.filterIndexed { i, _ -> ((days shr i) and 1) == 1 }.joinToString(" ")
    }

    data class Occurrence(val alarm: AlarmEntry, val epochDay: Long, val timeMs: Long) {
        /** Identifica esta ocurrencia concreta (alarma + día) para no repetirla. */
        val stamp: String get() = "$epochDay#${alarm.id}"
    }

    private fun epochDay(c: Calendar): Long = LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)).toEpochDay()

    /** Próxima ocurrencia futura de las alarmas activas, ignorando la que ya sonó ([ringedStamp]). */
    fun next(alarms: List<AlarmEntry>, now: Long, ringedStamp: String?): Occurrence? {
        for (offset in 0..7) {
            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
            val dow = day.get(Calendar.DAY_OF_WEEK)
            var best: Occurrence? = null
            for (a in alarms) {
                if (!a.enabled || (a.days and bit(dow)) == 0) continue
                val t = Calendar.getInstance().apply {
                    timeInMillis = day.timeInMillis
                    set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (t <= now) continue
                val occ = Occurrence(a, epochDay(day), t)
                if (occ.stamp == ringedStamp) continue
                if (best == null || t < best.timeMs) best = occ
            }
            if (best != null) return best
        }
        return null
    }

    /** Una alarma activa que suena en menos de [lockHours] horas no se puede cambiar, apagar ni borrar. */
    fun isLocked(a: AlarmEntry, now: Long, ringedStamp: String?, lockHours: Long): Boolean {
        if (!a.enabled) return false
        val occ = next(listOf(a), now, ringedStamp) ?: return false
        return occ.timeMs - now < lockHours * 3_600_000L
    }
}
