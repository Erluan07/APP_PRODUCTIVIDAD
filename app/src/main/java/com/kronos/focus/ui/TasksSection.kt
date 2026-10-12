package com.kronos.focus.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.TaskTemplate

/** "Pendientes de hoy" (tareas sin hora) y "Mis tareas" (lista editable e importación). */
@Composable
fun TasksSection(
    ctx: Context,
    now: Long,
    bunkerActive: Boolean,
    onImportFile: () -> Unit,
    onPaste: () -> Unit,
    onStartTask: (Long) -> Unit,
) {
    val agenda = AgendaStore.get(ctx)
    val templates = TemplateStore.get(ctx)
    var tick by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_EXPRESSION") now; tick

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pendientes de hoy (sin hora)", style = MaterialTheme.typography.titleSmall)
        val flex = agenda.flex()
        if (flex.isEmpty()) Text("Ninguno.", style = MaterialTheme.typography.bodySmall)
        flex.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (f.done) "✔ ${f.name}" else f.name, modifier = Modifier.weight(1f))
                if (!f.done) {
                    Button(enabled = !bunkerActive, onClick = { picking = f.name }) { Text("Empezar") }
                    OutlinedButton(onClick = { agenda.setFlexDone(f.name, true); tick++ }) { Text("Hecha") }
                }
            }
        }
        Text("“Hecha” aún no pide foto de evidencia (llega en la Fase 4).", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Mis tareas (las eliges en el ritual matutino)", style = MaterialTheme.typography.titleSmall)
        val list = templates.all()
        if (list.isEmpty()) Text("Aún no tienes tareas. Agrega o importa.", style = MaterialTheme.typography.bodySmall)
        list.forEach { t ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.label(), modifier = Modifier.weight(1f))
                TextButton(onClick = { templates.remove(t.name); tick++ }) { Text("Borrar") }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Nueva tarea (ej. Gimnasio)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, label = { Text("Inicio (1800)") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("Fin (1930)") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
        Text("Sin inicio ni fin = tarea flexible (no sabes cuándo ni cuánto).", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            val s = AgendaValidator.parseHm(start); val e = AgendaValidator.parseHm(end)
            when {
                name.isBlank() -> error = "Escribe el nombre."
                start.isBlank() && end.isBlank() -> { templates.upsert(TaskTemplate(name.trim())); name = ""; error = null; tick++ }
                s == null || e == null || e <= s -> error = "Horas inválidas (24 h, fin > inicio) o deja ambas vacías."
                else -> { templates.upsert(TaskTemplate(name.trim(), s, e)); name = ""; start = ""; end = ""; error = null; tick++ }
            }
        }) { Text("Guardar tarea") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onImportFile(); tick++ }) { Text("Importar archivo") }
            OutlinedButton(onClick = { onPaste(); tick++ }) { Text("Pegar del portapapeles") }
        }
        Text("Importa un .json o .txt exportado de tu app de tareas, o copia la lista y pégala.", style = MaterialTheme.typography.bodySmall)
    }

    picking?.let { task ->
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {},
            title = { Text("¿Cuánto tiempo para “$task”?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se activa el Modo Búnker y no se puede cancelar.")
                    listOf(25L, 45L, 60L, 90L, 120L).forEach { m ->
                        Button(onClick = { onStartTask(m); picking = null; tick++ }, modifier = Modifier.fillMaxWidth()) { Text("$m min") }
                    }
                }
            }
        )
    }
}
