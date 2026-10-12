package com.kronos.focus.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.TaskManagerClient
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.TaskTemplate
import com.kronos.focus.services.AgendaActions

/** Nombre + hora de inicio/fin (selector de reloj). Sin horas = tarea flexible. */
@Composable
private fun TaskForm(button: String, onSubmit: (String, Int?, Int?) -> String?) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf<Int?>(null) }
    var end by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre de la tarea") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("Inicio", start, { start = it }, Modifier.weight(1f))
            TimeField("Fin", end, { end = it }, Modifier.weight(1f))
        }
        Text("Sin hora = tarea flexible (no sabes cuándo ni cuánto).", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            val s = start; val e = end
            val err = when {
                name.isBlank() -> "Escribe el nombre."
                (s == null) != (e == null) -> "Pon inicio y fin, o deja ambos sin hora."
                s != null && e != null && e <= s -> "El fin debe ser posterior al inicio."
                else -> onSubmit(name.trim(), s, e)
            }
            error = err
            if (err == null) { name = ""; start = null; end = null }
        }) { Text(button) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** "Pendientes de hoy", "Agregar al día" y "Mis tareas" (lista editable, importación, Task Manager). */
@Composable
fun TasksSection(
    ctx: Context,
    now: Long,
    bunkerActive: Boolean,
    onImportFile: () -> Unit,
    onPaste: () -> Unit,
    onStartTask: (Long) -> Unit,
    onProof: (String, String) -> Unit,
) {
    val agenda = AgendaStore.get(ctx)
    val templates = TemplateStore.get(ctx)
    var tick by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_EXPRESSION") now; tick

    fun addToday(t: TaskTemplate): String? =
        if (t.isTimed) AgendaActions.addBlock(ctx, AgendaBlock(t.name, AgendaValidator.todayAt(t.startMin!!), AgendaValidator.todayAt(t.endMin!!)))
        else AgendaActions.addFlex(ctx, t.name)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pendientes de hoy (sin hora)", style = MaterialTheme.typography.titleSmall)
        val flex = agenda.flex()
        if (flex.isEmpty()) Text("Ninguno.", style = MaterialTheme.typography.bodySmall)
        flex.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (f.done) "✔ ${f.name}" else f.name, modifier = Modifier.weight(1f))
                if (!f.done) {
                    Button(enabled = !bunkerActive, onClick = { picking = f.name }) { Text("Empezar") }
                    OutlinedButton(onClick = { onProof(f.name, "flex") }) { Text("Hecha 📷") }
                }
            }
        }
        Text("Para marcar una tarea como hecha tomas una foto de evidencia (cámara en vivo).", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Agregar una tarea a hoy", style = MaterialTheme.typography.titleSmall)
        Text("Solo se pueden agregar tareas; las ya confirmadas no se quitan.", style = MaterialTheme.typography.bodySmall)
        TaskForm("Agregar a mi día") { n, s, e ->
            if (s == null || e == null) AgendaActions.addFlex(ctx, n)
            else AgendaActions.addBlock(ctx, AgendaBlock(n, AgendaValidator.todayAt(s), AgendaValidator.todayAt(e)))
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider()
        Text("Mis tareas", style = MaterialTheme.typography.titleSmall)
        val list = templates.all()
        if (list.isEmpty()) Text("Aún no tienes tareas. Agrega o importa.", style = MaterialTheme.typography.bodySmall)
        list.forEach { t ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.label(), modifier = Modifier.weight(1f))
                TextButton(onClick = { msg = addToday(t); tick++ }) { Text("Hoy") }
                TextButton(onClick = { templates.remove(t.name); tick++ }) { Text("Borrar") }
            }
        }
        TaskForm("Guardar en Mis tareas") { n, s, e -> templates.upsert(TaskTemplate(n, s, e)); null }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onImportFile(); tick++ }) { Text("Importar archivo") }
            OutlinedButton(onClick = { onPaste(); tick++ }) { Text("Pegar del portapapeles") }
        }

        HorizontalDivider()
        Text("De Task Manager (pendientes)", style = MaterialTheme.typography.titleSmall)
        val tm = remember(tick) { TaskManagerClient.pending(ctx) }
        when (tm) {
            is TaskManagerClient.Result.Ok -> {
                if (tm.tasks.isEmpty()) Text("Sin pendientes.", style = MaterialTheme.typography.bodySmall)
                tm.tasks.take(30).forEach { t ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${t.title}  [${t.priority}${t.dueDate?.let { " · $it" } ?: ""}]", modifier = Modifier.weight(1f))
                        TextButton(onClick = { msg = AgendaActions.addFlex(ctx, t.title); tick++ }) { Text("Hoy") }
                    }
                }
            }
            TaskManagerClient.Result.NotAvailable ->
                Text("No conectado (instala la versión de Task Manager con el proveedor).", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { tick++ }) { Text("Actualizar lista") }
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
