package com.kronos.focus.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kronos.focus.BuildConfig
import com.kronos.focus.data.*
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.services.AgendaActions
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.services.AwakeWatchService
import com.kronos.focus.services.BunkerWatchdogService
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.ProofActivity
import com.kronos.focus.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TEST_BUNKER_MIN = 5L

internal fun fmtHm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

internal fun fmtCountdown(sec: Long): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%02d:%02d".format(sec / 60, sec % 60)

/** Un renglón de la línea de tiempo: bloque de Kronos o evento del calendario. */
private data class Slot(val title: String, val startMs: Long, val endMs: Long, val isBlock: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(ctx: Context, now: Long, a11y: Boolean, actions: AppActions) {
    val store = BunkerStateStore.get(ctx)
    val agenda = AgendaStore.get(ctx)
    val proofs = ProofStore.get(ctx)
    var showAdd by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    val active = store.isActive(now)
    val sleeping = store.isSleepMode(now)
    val remainingSec = store.remainingMs(now) / 1000
    val calOk = CalendarReader.hasPermission(ctx)
    val events = remember(now / 60_000L, calOk) { CalendarReader.eventsForDay(ctx, now) }
    val dateText = remember(now / 3_600_000L) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date(now)).replaceFirstChar { it.uppercase() }
    }

    ScreenScaffold(fab = { AddFab("Tarea", Icons.Filled.Add) { showAdd = true } }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(dateText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            StatusPill(if (active) (if (sleeping) "Dormir" else "Búnker") else "Libre", if (active) Tone.INFO else Tone.OK)
        }

        if (active) {
            HeroCard {
                Text(if (sleeping) "Modo Dormir" else "Modo Búnker", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
                Text(fmtCountdown(remainingSec), style = MaterialTheme.typography.displayMedium, color = Color.White)
                Text(
                    if (sleeping) "Solo Teléfono y Spotify hasta tu alarma." else "Permitido: Teléfono, WhatsApp, Spotify, Mensajes y Contactos.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
                if (BuildConfig.DEBUG) {
                    TextButton(
                        onClick = { store.debugClear() }, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("Salir del búnker (solo versión de prueba)") }
                }
            }
        } else {
            HeroCard {
                Text("Listo para enfocar", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Tus bloques de agenda activan el búnker solos.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                CtaButton("Iniciar búnker de $TEST_BUNKER_MIN min", enabled = a11y, onClick = {
                    store.startSession(TEST_BUNKER_MIN)
                    BunkerWatchdogService.start(ctx)
                })
            }
        }

        if (!a11y) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    SectionTitle("El servicio de bloqueo está apagado", "Sin él Kronos no puede bloquear apps.")
                }
                CtaButton("Activar servicio de accesibilidad", onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
                MutedText("Si Android lo marca como restringido: Ajustes > Apps > Kronos Focus > ⋮ > Permitir ajustes restringidos.")
            }
        }

        // Alarma: una línea clara con la hora real y desde cuándo puede adelantarse el despertar inteligente
        val next = AlarmScheduler.nextOccurrence(ctx, now)
        if (next == null) {
            IconText(Icons.Filled.Notifications, "Sin alarmas activas. Crea una en la pestaña Alarmas.")
        } else {
            val early = next.timeMs - SleepLogStore.get(ctx).params().windowMin * 60_000L
            IconText(
                Icons.Filled.Notifications,
                "Alarma: " + SimpleDateFormat("EEEE HH:mm", Locale("es")).format(Date(next.timeMs)) + " · puede adelantarse desde las " + fmtHm(early),
            )
        }
        if (AwakeWatchService.running) StatusPill("Vigilando que no te vuelvas a dormir", Tone.INFO)

        // Línea de tiempo del día
        SectionTitle("Tu día")
        if (!calOk) {
            AppCard {
                SectionTitle("Conecta tu calendario", "Para ver tus reuniones junto a tus tareas.")
                SoftButton("Permitir calendario", { actions.requestCalendar() }, Modifier.fillMaxWidth())
            }
        }
        events.filter { it.allDay }.forEach { MutedText("Todo el día: ${it.title}") }
        val slots = (events.filter { !it.allDay }.map { Slot(it.title, it.startMs, it.endMs, false) } +
            agenda.blocks().map { Slot(it.name, it.startMs, it.endMs, true) }).sortedBy { it.startMs }
        if (slots.isEmpty()) MutedText("Aún no hay nada en tu agenda. Toca “Tarea” para agregar.")
        var markerShown = false
        slots.forEach { sl ->
            if (!markerShown && sl.startMs > now) {
                markerShown = true
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ahora ${fmtHm(now)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }
            }
            val proved = sl.isBlock && remember(sl.title, now / 3000) { proofs.hasProofToday(sl.title) }
            val clash = if (sl.isBlock) CalendarReader.conflicts(events, sl.startMs, sl.endMs).firstOrNull() else null
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(48.dp)) {
                    Text(fmtHm(sl.startMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    MutedText(fmtHm(sl.endMs))
                }
                Surface(
                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                    color = if (sl.isBlock) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(5.dp).fillMaxHeight().background(if (sl.isBlock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(sl.title, style = MaterialTheme.typography.titleSmall, color = if (sl.isBlock) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            if (!sl.isBlock) MutedText("Calendario")
                            if (sl.isBlock) {
                                val (label, tone) = when {
                                    proved -> "Con foto" to Tone.OK
                                    now >= sl.endMs -> "Falta foto" to Tone.WARN
                                    now >= sl.startMs -> "En curso" to Tone.INFO
                                    else -> "Pendiente" to Tone.INFO
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusPill(label, tone)
                                    if (!proved && now >= sl.startMs) TextAction("Foto", { actions.openProof(sl.title, ProofActivity.KIND_BLOCK) })
                                }
                            }
                            clash?.let { StatusPill("Choca con: ${it.title}", Tone.WARN) }
                        }
                    }
                }
            }
        }

        // Pendientes sin hora, como lista de verificación
        val flex = agenda.flex()
        if (flex.isNotEmpty()) {
            SectionTitle("Pendientes sin hora", "Pulsa el círculo al terminar (te pide una foto).")
            flex.forEach { f ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = !f.done, onClick = { actions.openProof(f.name, ProofActivity.KIND_FLEX) }) {
                            Icon(
                                Icons.Filled.CheckCircle, contentDescription = if (f.done) "Hecha" else "Marcar como hecha con foto",
                                tint = if (f.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (!f.done) TextAction("Empezar", { picking = f.name }, enabled = !active)
                    }
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agregar a hoy", style = MaterialTheme.typography.titleLarge)
                MutedText("Solo se pueden agregar tareas; las ya confirmadas no se quitan.")
                TaskForm("Agregar a mi día", onSubmit = { n, s, e ->
                    if (s == null || e == null) AgendaActions.addFlex(ctx, n)
                    else AgendaActions.addBlock(ctx, AgendaBlock(n, AgendaValidator.todayAt(s), AgendaValidator.todayAt(e)))
                }, onSuccess = { showAdd = false })
            }
        }
    }

    picking?.let { task ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("¿Cuánto tiempo para “$task”?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedText("Se activa el Modo Búnker y no se puede cancelar.")
                    listOf(25L, 45L, 60L, 90L, 120L).forEach { m ->
                        SoftButton("$m min", {
                            store.startSession(m)
                            BunkerWatchdogService.start(ctx)
                            picking = null
                        }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextAction("Cancelar", { picking = null }) },
        )
    }
}
