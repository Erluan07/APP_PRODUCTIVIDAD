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
import com.kronos.focus.data.TaskManagerClient
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.TaskTemplate
import com.kronos.focus.services.AgendaActions
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.components.*

@Composable
fun TasksScreen(ctx: Context, now: Long, actions: AppActions) {
    val templates = TemplateStore.get(ctx)
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    val list = remember(tick, now / 2000) { templates.all() }
    val tm = remember(tick) { TaskManagerClient.pending(ctx) }

    fun addToday(t: TaskTemplate): String? =
        if (t.isTimed) AgendaActions.addBlock(ctx, AgendaBlock(t.name, AgendaValidator.todayAt(t.startMin!!), AgendaValidator.todayAt(t.endMin!!)))
        else AgendaActions.addFlex(ctx, t.name)

    ScreenScaffold("Tareas", "Mis tareas y Task Manager") {
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        AppCard {
            SectionTitle("Mis tareas", "Las eliges en el ritual matutino o las sumas a hoy.")
            if (list.isEmpty()) MutedText("Aún no tienes tareas. Crea una abajo o importa.")
            list.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        MutedText(if (t.isTimed) "${t.hm(t.startMin!!)} – ${t.hm(t.endMin!!)}" else "Sin hora")
                    }
                    TextAction("Hoy", { msg = addToday(t) })
                    IconButton(onClick = { templates.remove(t.name); tick++ }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar ${t.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        AppCard {
            SectionTitle("Nueva tarea", "Con hora fija (ej. Gimnasio 18:00–19:30) o sin hora.")
            TaskForm("Guardar en Mis tareas", onSubmit = { n, s, e -> templates.upsert(TaskTemplate(n, s, e)); tick++; null })
        }

        AppCard {
            SectionTitle("De Task Manager", "Pendientes de tu app de tareas")
            when (tm) {
                is TaskManagerClient.Result.Ok -> {
                    if (tm.tasks.isEmpty()) MutedText("Sin pendientes.")
                    tm.tasks.take(30).forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                MutedText("Prioridad ${t.priority}" + (t.dueDate?.let { " · $it" } ?: ""))
                            }
                            TextAction("Hoy", { msg = AgendaActions.addFlex(ctx, t.title) })
                        }
                    }
                }
                TaskManagerClient.Result.NotAvailable ->
                    MutedText("No conectado. Instala la versión de Task Manager con el proveedor de datos.")
            }
            SoftButton("Actualizar lista", { tick++ })
        }

        AppCard {
            SectionTitle("Importar", "Desde un archivo .json o .txt, o desde el portapapeles.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Archivo", { actions.importFile(); tick++ }, Modifier.weight(1f))
                SoftButton("Portapapeles", { actions.paste(); tick++ }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
