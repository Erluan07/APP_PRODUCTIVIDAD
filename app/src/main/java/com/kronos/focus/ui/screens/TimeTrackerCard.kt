package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.TimeTrackerStore
import com.kronos.focus.domain.time.TimeSpans
import com.kronos.focus.ui.components.*

/** "Tiempos de hoy": categorías con meta diaria y botón Empezar/Parar. Solo es contabilidad. */
@Composable
fun TimeTrackerCard(ctx: Context, now: Long) {
    val store = TimeTrackerStore.get(ctx)
    val refresh = com.kronos.focus.ui.LocalRefresh.current
    var tick by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val cats = remember(tick) { store.categories() }
    val running = store.running()
    AppCard {
        SectionTitle("Tiempos de hoy", "Dale a Empezar al arrancar y a Parar al terminar: se acumula contra tu meta diaria.")
        cats.forEach { c ->
            val ms = store.todayMs(c.id, now)
            val goalMs = c.goalMin * 60_000L
            val isRunning = running?.first == c.id
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            TimeSpans.fmt(ms, seconds = isRunning) + " de " + TimeSpans.fmt(goalMs) + " h" +
                                if (ms >= goalMs) " · ¡meta cumplida!" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ms >= goalMs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isRunning) CtaButton("Parar", { store.stop(); tick++; refresh() }, Modifier.width(120.dp))
                    else SoftButton("Empezar", { store.start(c.id); tick++; refresh() }, Modifier.width(120.dp))
                    IconButton(onClick = { store.removeCategory(c.id); tick++; refresh() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar ${c.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LinearProgressIndicator(
                    progress = { (ms.toFloat() / goalMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                )
                MutedText("Últimos 7 días: " + TimeSpans.fmt(store.daysMs(c.id, now, 7)) + " h")
            }
        }
        if (cats.isEmpty()) MutedText("Sin categorías. Añade una, por ejemplo Trabajo con 8 h al día.")
        if (adding) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre (p. ej. Estudio)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                hours, { hours = it }, label = { Text("Meta diaria en horas (p. ej. 2 o 1.5)") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            val goal = hours.replace(',', '.').toDoubleOrNull()?.let { (it * 60).toInt() } ?: 0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtaButton("Añadir", { if (store.addCategory(name, goal)) { name = ""; hours = ""; adding = false; tick++ } }, Modifier.weight(1f), enabled = name.isNotBlank() && goal > 0)
                SoftButton("Cancelar", { adding = false }, Modifier.weight(1f))
            }
        } else SoftButton("Añadir categoría", { adding = true }, Modifier.fillMaxWidth())
    }
}
