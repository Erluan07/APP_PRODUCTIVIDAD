package com.kronos.focus.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
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
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.LocalRefresh
import com.kronos.focus.ui.ProofActivity
import com.kronos.focus.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val refresh = LocalRefresh.current
    val midnight = com.kronos.focus.ui.theme.UiDesign.current == com.kronos.focus.ui.theme.UiDesign.MIDNIGHT
    var showAdd by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    val active = store.isActive(now)
    val sleeping = store.isSleepMode(now)
    val remainingSec = store.remainingMs(now) / 1000
    val mustNow = remember(now / 1000L, showAdd) { agenda.activeMust(now) }
    // Bloqueado solo por una tarea obligatoria (no hay cuenta atrás que mostrar).
    val mustOnlyName = if (active && !sleeping && remainingSec <= 0L) mustNow?.name else null
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

        if (midnight) {
            MidnightHero(store, now, active, sleeping, remainingSec, a11y,
                onBunker = { showStart = true },
                onSleep = { actions.startSleep(); refresh() },
                onBigScreen = { com.kronos.focus.ui.TimerActivity.open(ctx) },
                mustName = mustOnlyName)
        } else if (active) {
            HeroCard {
                Text(if (sleeping) "Modo Dormir" else "Modo Búnker", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
                if (mustOnlyName != null) Text("Pendiente: $mustOnlyName", style = MaterialTheme.typography.titleLarge, color = Color.White)
                else Text(fmtCountdown(remainingSec), style = MaterialTheme.typography.displayMedium, color = Color.White)
                val pomo = if (sleeping) null else store.pomodoroPhase(now)
                pomo?.let {
                    Text(
                        (if (it.focus) "Pomodoro · enfoque " else "Pomodoro · descanso, estira ") + fmtCountdown(it.remainingSec),
                        style = MaterialTheme.typography.titleMedium, color = Color.White,
                    )
                }
                Text(
                    if (sleeping) "Solo Teléfono y Spotify hasta tu alarma." else "Permitido: Teléfono, WhatsApp, Task Manager IA, Bancolombia, Spotify, Mensajes y Contactos.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
                SoftButton("Pantalla grande (horizontal)", { com.kronos.focus.ui.TimerActivity.open(ctx) }, Modifier.fillMaxWidth())
            }
        } else {
            HeroCard {
                Text("Listo para enfocar", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Tus bloques de agenda activan el búnker solos.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                CtaButton("Iniciar Modo Búnker", enabled = a11y, onClick = { showStart = true })
                CtaButton("Me voy a dormir (Modo Dormir)", enabled = a11y, icon = Icons.Filled.Notifications, onClick = {
                    actions.startSleep(); refresh()
                })
            }
        }

        // Tareas obligatorias pendientes: bloquean el teléfono desde su hora hasta subir la evidencia
        val musts = remember(now / 1000L, showAdd) { agenda.musts().filter { !it.done } }
        if (musts.isNotEmpty()) {
            SectionTitle("Tareas obligatorias", "El teléfono se bloquea desde la hora de inicio hasta que subas la foto de evidencia.")
            musts.forEach { m ->
                val started = now >= m.startMs
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        StatusPill(if (started) "Bloqueando desde ${fmtHm(m.startMs)}" else "Empieza ${fmtHm(m.startMs)}", if (started) Tone.ERROR else Tone.INFO)
                    }
                    CtaButton("Ya la hice · tomar foto", { actions.openProof(m.name, ProofActivity.KIND_MUST) })
                }
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
        if (AwakeWatchService.running) StatusPill("Vigilando: camina cada 4 min o vuelve a sonar", Tone.INFO)

        // Línea de tiempo del día
        SectionTitle("Tu día")
        if (!calOk) {
            AppCard {
                SectionTitle("Conecta tu calendario", "Para ver tus reuniones junto a tus tareas.")
                SoftButton("Permitir calendario", { actions.requestCalendar() }, Modifier.fillMaxWidth())
            }
        }
        events.filter { it.allDay }.forEach { MutedText("Todo el día: ${it.title}") }
        val slots = remember(events, now / 10_000L, showAdd) {
            (events.filter { !it.allDay }.map { Slot(it.title, it.startMs, it.endMs, false) } +
                agenda.blocks().map { Slot(it.name, it.startMs, it.endMs, true) }).sortedBy { it.startMs }
        }
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
            val proved = sl.isBlock && remember(sl.title, now / 10_000L) { proofs.hasProofToday(sl.title) }
            val clash = if (sl.isBlock) CalendarReader.conflicts(events, sl.startMs, sl.endMs).firstOrNull() else null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        val flex = remember(now / 10_000L, picking, showAdd) { agenda.flex() }
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
                var mustMode by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !mustMode, onClick = { mustMode = false }, label = { Text("Tarea normal") })
                    FilterChip(selected = mustMode, onClick = { mustMode = true }, label = { Text("Obligatoria") })
                }
                if (mustMode) {
                    MustTaskForm(onSubmit = { n, startMin, verify ->
                        AgendaActions.addMust(ctx, n, AgendaValidator.todayAt(startMin), verify).also { if (it == null) refresh() }
                    }, onSuccess = { showAdd = false })
                } else {
                    MutedText("Solo se pueden agregar tareas; las ya confirmadas no se quitan.")
                    TaskForm("Agregar a mi día", onSubmit = { n, s, e ->
                        if (s == null || e == null) AgendaActions.addFlex(ctx, n)
                        else AgendaActions.addBlock(ctx, AgendaBlock(n, AgendaValidator.todayAt(s), AgendaValidator.todayAt(e)))
                    }, onSuccess = { showAdd = false })
                }
            }
        }
    }

    if (showStart) {
        BunkerStartDialog(
            onDismiss = { showStart = false },
            onStart = { minutes, pomodoro ->
                store.startSession(minutes, pomodoro)
                BunkerWatchdogService.start(ctx)
                showStart = false
                refresh()
            },
        )
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
                            refresh()
                            picking = null
                        }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextAction("Cancelar", { picking = null }) },
        )
    }
}

/** Elige cuánto dura el búnker (sin tarea asociada) y si se divide en Pomodoros de 50/10. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BunkerStartDialog(onDismiss: () -> Unit, onStart: (Long, Boolean) -> Unit) {
    val max = AppRulesConfig.MAX_SINGLE_BUNKER_MIN
    var minutes by remember { mutableStateOf("60") }
    var pomodoro by remember { mutableStateOf(false) }
    val value = minutes.toLongOrNull()
    val valid = value != null && value in 5..max
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Iniciar Modo Búnker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MutedText("Elige cuánto tiempo. Una vez iniciado no se puede cancelar.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25L, 45L, 60L, 90L, 120L, 180L, 240L).forEach { m ->
                        FilterChip(selected = value == m, onClick = { minutes = m.toString() }, label = { Text(if (m % 60 == 0L) "${m / 60} h" else "$m min") })
                    }
                }
                OutlinedTextField(
                    minutes, { s -> minutes = s.filter { it.isDigit() }.take(3) }, singleLine = true,
                    label = { Text("Minutos (5 a $max)") }, isError = !valid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Pomodoro", style = MaterialTheme.typography.titleSmall)
                        MutedText("${AppRulesConfig.POMODORO_FOCUS_MIN} min enfocado + ${AppRulesConfig.POMODORO_BREAK_MIN} de descanso. En el descanso solo te aviso para estirar; las apps siguen bloqueadas.")
                    }
                    Switch(checked = pomodoro, onCheckedChange = { pomodoro = it })
                }
            }
        },
        confirmButton = { TextAction("Iniciar", { value?.let { onStart(it, pomodoro) } }, enabled = valid) },
        dismissButton = { TextAction("Cancelar", onDismiss) },
    )
}

/** Diseño Medianoche: anillo de enfoque grande + dos botones cuadrados, en lugar de una tarjeta ancha. */
@Composable
private fun MidnightHero(
    store: BunkerStateStore, now: Long, active: Boolean, sleeping: Boolean, remainingSec: Long, a11y: Boolean,
    onBunker: () -> Unit, onSleep: () -> Unit, onBigScreen: () -> Unit, mustName: String?,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val pomo = if (active && !sleeping) store.pomodoroPhase(now) else null
        FocusRing(progress = if (active) store.progress(now) else 0f) {
            if (active && mustName != null) {
                Text("OBLIGATORIA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(mustName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                MutedText("bloqueado hasta la evidencia")
            } else if (active) {
                Text(
                    if (sleeping) "MODO DORMIR" else if (pomo != null) (if (pomo.focus) "ENFOQUE" else "DESCANSO") else "BÚNKER",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                )
                Text(fmtCountdown(if (pomo != null) pomo.remainingSec else remainingSec), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
                MutedText(if (pomo != null) "Total ${fmtCountdown(remainingSec)}" else if (sleeping) "hasta tu alarma" else "restante")
            } else {
                Text("LIBRE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Listo", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                MutedText("para enfocar")
            }
        }
        if (active) {
            MutedText(
                if (mustName != null) "Hasta que subas la foto de evidencia solo funcionan tus apps habilitadas."
                else if (sleeping) "Solo Teléfono y Spotify hasta tu alarma."
                else "Solo tus apps habilitadas, Teléfono y lo esencial del sistema.",
            )
            SoftButton("Pantalla grande (horizontal)", onBigScreen, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("Modo Búnker", "Elige el tiempo", Icons.Filled.Lock, onBunker, Modifier.weight(1f), enabled = a11y)
                ActionTile("Me voy a dormir", "Hasta tu alarma", Icons.Filled.Notifications, onSleep, Modifier.weight(1f), enabled = a11y)
            }
        }
    }
}
