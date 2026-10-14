package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.TaskManagerClient
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.TaskTemplate
import com.kronos.focus.services.AgendaActions
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(ctx: Context, now: Long, actions: AppActions) {
    val templates = TemplateStore.get(ctx)
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val list = remember(tick, now / 2000) { templates.all() }
    val tm = remember(tick) { TaskManagerClient.pending(ctx) }

    var confirmMust by remember { mutableStateOf<TaskTemplate?>(null) }

    fun addToday(t: TaskTemplate): String? =
        if (t.isMust) { confirmMust = t; null }
        else if (t.isTimed) AgendaActions.addBlock(ctx, AgendaBlock(t.name, AgendaValidator.todayAt(t.startMin!!), AgendaValidator.todayAt(t.endMin!!)))
        else AgendaActions.addFlex(ctx, t.name)

    ScreenScaffold(fab = { AddFab("Tarea", Icons.Filled.Add) { showNew = true } }) {
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        SectionTitle("Mis tareas", "Las eliges en el ritual matutino o las sumas a hoy.")
        if (list.isEmpty()) MutedText("Aún no tienes tareas. Toca “Tarea” para crear una.")
        list.forEach { t ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        MutedText(if (t.isMust) "Obligatoria · bloquea desde ${t.hm(t.startMin!!)}" else if (t.isTimed) "${t.hm(t.startMin!!)} – ${t.hm(t.endMin!!)}" else "Sin hora")
                    }
                    TextAction("Hoy", { msg = addToday(t) })
                    IconButton(onClick = { templates.remove(t.name); tick++ }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar ${t.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionTitle("De Task Manager", "Pendientes de tu app de tareas")
        when (tm) {
            is TaskManagerClient.Result.Ok -> {
                if (tm.tasks.isEmpty()) MutedText("Sin pendientes.")
                tm.tasks.take(30).forEach { t ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                MutedText("Prioridad ${t.priority}" + (t.dueDate?.let { " · $it" } ?: ""))
                            }
                            TextAction("Hoy", { msg = AgendaActions.addFlex(ctx, t.title) })
                        }
                    }
                }
            }
            TaskManagerClient.Result.NotAvailable ->
                MutedText("No conectado. Instala la versión de Task Manager con el proveedor de datos.")
        }
        SoftButton("Actualizar lista", { tick++ }, Modifier.fillMaxWidth())

        SectionTitle("Importar", "Desde un archivo .json o .txt, o desde el portapapeles.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Archivo", { actions.importFile(); tick++ }, Modifier.weight(1f))
            SoftButton("Portapapeles", { actions.paste(); tick++ }, Modifier.weight(1f))
        }
    }

    if (showNew) {
        ModalBottomSheet(onDismissRequest = { showNew = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nueva tarea", style = MaterialTheme.typography.titleLarge)
                var mustMode by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !mustMode, onClick = { mustMode = false }, label = { Text("Normal") })
                    FilterChip(selected = mustMode, onClick = { mustMode = true }, label = { Text("Obligatoria") })
                }
                if (mustMode) {
                    MutedText("Se guarda como plantilla; al sumarla a hoy bloquea el teléfono desde su hora hasta subir la evidencia.")
                    MustTaskForm(onSubmit = { n, s -> templates.upsert(TaskTemplate(n, s, null, must = true)); tick++; null }, onSuccess = { showNew = false }, button = "Guardar en Mis tareas", confirmFirst = false)
                } else {
                    MutedText("Con hora fija (ej. Gimnasio 18:00–19:30) o sin hora.")
                    TaskForm("Guardar en Mis tareas", onSubmit = { n, s, e -> templates.upsert(TaskTemplate(n, s, e)); tick++; null }, onSuccess = { showNew = false })
                }
            }
        }
    }

    confirmMust?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmMust = null },
            title = { Text("¿Sumar “${t.name}” a hoy?") },
            text = { Text("Bloqueará el teléfono desde las ${t.hm(t.startMin!!)} (si ya pasó, desde ahora) hasta que subas la foto de evidencia. No se puede cancelar.") },
            confirmButton = {
                TextAction("Confirmar", { msg = AgendaActions.addMust(ctx, t.name, AgendaValidator.todayAt(t.startMin)); confirmMust = null })
            },
            dismissButton = { TextAction("Cancelar", { confirmMust = null }) },
        )
    }
}
