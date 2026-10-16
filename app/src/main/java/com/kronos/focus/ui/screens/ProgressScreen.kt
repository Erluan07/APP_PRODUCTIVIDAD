package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.ProofStore
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.data.StatsStore
import com.kronos.focus.domain.stats.StreakCalc
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.ui.components.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@Composable
private fun DayDot(letter: String, state: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(letter, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val (bg, fg) = when (state) {
            1 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            2 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(shape = CircleShape, color = bg, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                when (state) {
                    1 -> Icon(Icons.Filled.Check, contentDescription = "Cumplido", tint = fg, modifier = Modifier.size(18.dp))
                    2 -> Icon(Icons.Filled.Close, contentDescription = "No cumplido", tint = fg, modifier = Modifier.size(18.dp))
                    else -> Text(if (state == 3) "•" else "", color = fg)
                }
            }
        }
    }
}

@Composable
fun ProgressSection(ctx: Context, now: Long) {
    val today = AlarmScheduler.epochDay()
    val days = remember(now / 10_000) { StatsStore.get(ctx).all() }
    val sleep = SleepLogStore.get(ctx)
    val proofs = ProofStore.get(ctx)
    val avgSleep = remember(now / 10_000) { sleep.avgSleepMin() }
    val avgRating = remember(now / 10_000) { sleep.avgRating() }
    val total = remember(now / 5_000) { proofs.count() }
    val intact = remember(now / 10_000) { proofs.verify() }
    val recent = remember(now / 10_000) { proofs.recent(5) }
    val letters = "LMXJVSD"

    ScreenScaffold {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Racha actual", "${StreakCalc.current(days, today)} días", Modifier.weight(1f))
            StatTile("Mejor racha", "${StreakCalc.best(days)} días", Modifier.weight(1f))
        }

        AppCard {
            SectionTitle("Últimos 7 días", "Cumplido = todas las tareas hechas con foto.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (6 downTo 0).forEach { back ->
                    val d = today - back
                    val st = days[d]
                    val state = when {
                        st?.complete == true -> 1
                        st != null && st.tasks > 0 && d < today -> 2
                        d == today -> 3
                        else -> 0
                    }
                    DayDot(letters[LocalDate.ofEpochDay(d).dayOfWeek.value - 1].toString(), state)
                }
            }
            MutedText("Intentos bloqueados (7 días): ${(0..6).sumOf { days[today - it]?.blocked ?: 0 }}")
        }

        AppCard {
            SectionTitle("Sueño", "Promedio de tus últimas noches.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sueño medio", avgSleep?.let { "%dh %02dmin".format(it / 60, it % 60) } ?: "—", Modifier.weight(1f))
                StatTile("Valoración", avgRating?.let { "%.1f / 5".format(it) } ?: "—", Modifier.weight(1f))
            }
        }

        AppCard {
            SectionTitle("Evidencias")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("$total fotos", Tone.INFO)
                StatusPill(if (intact) "Registro íntegro" else "Registro alterado", if (intact) Tone.OK else Tone.ERROR)
            }
            if (recent.isEmpty()) MutedText("Aún no hay fotos de evidencia.")
            val fmt = SimpleDateFormat("d MMM HH:mm", Locale("es"))
            recent.forEach { p ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    MutedText(fmt.format(Date(p.ts)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
