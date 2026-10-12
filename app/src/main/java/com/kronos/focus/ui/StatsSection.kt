package com.kronos.focus.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.data.StatsStore
import com.kronos.focus.domain.stats.StreakCalc
import com.kronos.focus.services.AlarmScheduler
import java.time.LocalDate

/** Rachas, últimos 7 días y resumen de sueño. */
@Composable
fun StatsSection(ctx: Context, now: Long) {
    val today = AlarmScheduler.epochDay()
    val days = remember(now / 10_000) { StatsStore.get(ctx).all() }
    val sleep = SleepLogStore.get(ctx)
    val avgSleep = remember(now / 10_000) { sleep.avgSleepMin() }
    val avgRating = remember(now / 10_000) { sleep.avgRating() }
    val letters = "LMXJVSD"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Estadísticas", style = MaterialTheme.typography.titleSmall)
        Text("Racha actual: ${StreakCalc.current(days, today)} días · Mejor racha: ${StreakCalc.best(days)} días")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (6 downTo 0).forEach { back ->
                val d = today - back
                val st = days[d]
                val mark = when {
                    st?.complete == true -> "✔"
                    st != null && st.tasks > 0 && d < today -> "✖"
                    d == today -> "•"
                    else -> "·"
                }
                val letter = letters[LocalDate.ofEpochDay(d).dayOfWeek.value - 1]
                Text("$letter\n$mark", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Intentos bloqueados (7 días): ${(0..6).sumOf { days[today - it]?.blocked ?: 0 }}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Sueño medio: ${avgSleep?.let { "%dh %02dmin".format(it / 60, it % 60) } ?: "sin datos"} · Valoración media: ${avgRating?.let { "%.1f/5".format(it) } ?: "sin datos"}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
