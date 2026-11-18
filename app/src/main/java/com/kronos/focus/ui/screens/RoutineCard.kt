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
import com.kronos.focus.data.RoutineStore
import com.kronos.focus.ui.components.*

/** Editor de la rutina de la mañana. Bloqueado cuando está activada para mañana o en curso (no se puede recortar a última hora). */
@Composable
fun RoutineCard(ctx: Context, now: Long) {
    val store = RoutineStore.get(ctx)
    var tick by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    val steps = remember(tick, now / 2000) { store.steps() }
    val editable = remember(tick, now / 2000) { store.editable(ctx) }
    AppCard {
        SectionTitle("Rutina de la mañana", "Al apagar la alarma suena tu música y haces cada paso con foto de evidencia.")
        if (steps.isEmpty()) MutedText("Aún no tienes pasos. Ej.: cepillarme, tomar el batido de proteína.")
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. $s", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (editable) {
                    TextAction("↑", { store.move(s, -1); tick++ })
                    TextAction("↓", { store.move(s, 1); tick++ })
                    IconButton(onClick = { store.remove(s); tick++ }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar $s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (editable) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Nuevo paso") }, singleLine = true,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
            SoftButton("Añadir paso", { if (store.add(name)) { name = ""; tick++ } }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        } else {
            StatusPill("Bloqueada: está activada para mañana o en curso", Tone.WARN)
        }
        MutedText("Se activa con la pregunta al pulsar “Me voy a dormir”. La música es la última que reprodujiste en Spotify.")
    }
}
