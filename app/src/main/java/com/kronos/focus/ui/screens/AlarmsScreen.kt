package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.AlarmConfigStore
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.domain.alarm.AlarmEntry
import com.kronos.focus.domain.alarm.AlarmTimes
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.services.SleepTrackService
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.TimeField
import com.kronos.focus.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun DayToggle(letter: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.aspectRatio(1f).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                letter, style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Alarmas (lista grande con interruptor, como Google Reloj) + Modo Dormir. Editor en una hoja inferior. */
@Composable
fun AlarmsScreen(ctx: Context, now: Long, actions: AppActions) {
    val refresh = com.kronos.focus.ui.LocalRefresh.current
    val sleepLog = SleepLogStore.get(ctx)
    val params = sleepLog.params()
    val tracking = SleepTrackService.running
    val sleepingNow = BunkerStateStore.get(ctx).isSleepMode(now)
    val store = AlarmConfigStore.get(ctx)
    val ringed = AlarmStateStore.get(ctx).lastRingStamp
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AlarmEntry?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val alarms = remember(tick, now / 2000) { store.all().sortedBy { it.minutesOfDay } }
    val next = AlarmScheduler.nextOccurrence(ctx, now)

    fun save(a: AlarmEntry) { store.upsert(a); AlarmScheduler.scheduleDaily(ctx); tick++; refresh() }
    val lockedText = "No se puede cambiar una alarma que suena en menos de ${AppRulesConfig.ALARM_LOCK_HOURS} h."

    ScreenScaffold(fab = { AddFab("Alarma", Icons.Filled.Add) { msg = null; editing = AlarmEntry(0, 7, 0, AlarmTimes.ALL_DAYS, true, "") } }) {
        if (sleepLog.pendingRating()) {
            AppCard {
                SectionTitle("¿Cómo te sentiste al despertar?", "Tu respuesta ajusta solo el despertar inteligente.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n -> SoftButton("$n", { sleepLog.rate(n) }, Modifier.weight(1f)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MutedText("1 = fatal"); MutedText("5 = genial") }
            }
        }

        if (tracking || sleepingNow) {
            HeroCard {
                Text(if (sleepingNow) "Modo Dormir activo" else "Registrando tu sueño", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Termina cuando suene tu alarma. " + if (tracking) "Registro: acelerómetro" +
                        (if (AppRulesConfig.SLEEP_USE_MICROPHONE) " y volumen" else "") + "; deja el teléfono cargando." else "",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
            }
        } else {
            HeroCard {
                Text("¿Te vas a dormir?", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Activa el Modo Dormir hasta tu próxima alarma y registra tu sueño para despertarte en sueño ligero.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
                CtaButton("Me voy a dormir", { actions.startSleep(); refresh() }, icon = Icons.Filled.Notifications)
            }
        }

        if (next != null) {
            val early = next.timeMs - params.windowMin * 60_000L
            IconText(
                Icons.Filled.Notifications,
                "Suena " + SimpleDateFormat("EEEE HH:mm", Locale("es")).format(Date(next.timeMs)) +
                    " · puede adelantarse desde las " + fmtHm(early) + " si estás en sueño ligero",
            )
        } else {
            IconText(Icons.Filled.Notifications, "Sin alarmas activas. Toca “Alarma” para crear una.")
        }

        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        alarms.forEach { a ->
            val locked = AlarmTimes.isLocked(a, now, ringed, AppRulesConfig.ALARM_LOCK_HOURS)
            Surface(
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { if (locked) msg = lockedText else { msg = null; editing = a } },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            a.hm(), style = MaterialTheme.typography.displaySmall,
                            color = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MutedText(AlarmTimes.daysText(a.days) + if (a.label.isNotBlank()) " · ${a.label}" else "")
                        if (locked) StatusPill("Bloqueada: suena pronto", Tone.WARN, Modifier.padding(top = 4.dp))
                    }
                    Switch(
                        checked = a.enabled,
                        onCheckedChange = { on -> if (!on && locked) msg = lockedText else { msg = null; save(a.copy(enabled = on)) } },
                    )
                }
            }
        }
        if (alarms.isNotEmpty()) MutedText("Una alarma que suena en menos de ${AppRulesConfig.ALARM_LOCK_HOURS} h no se puede cambiar ni apagar.")

        AppCard {
            SectionTitle("Despertar inteligente", "Ventana ${params.windowMin} min · se ajusta con tu valoración")
            MutedText("Combina movimiento, sonido ambiente y el ciclo de 90 min. Es una estimación: un teléfono no mide fases de sueño.")
        }
    }

    editing?.let { e ->
        AlarmEditorSheet(
            e, canDelete = e.id != 0,
            onSave = { save(it); editing = null },
            onDelete = { store.delete(e.id); AlarmScheduler.scheduleDaily(ctx); tick++; refresh(); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorSheet(e: AlarmEntry, canDelete: Boolean, onSave: (AlarmEntry) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var time by remember(e.id) { mutableStateOf<Int?>(e.minutesOfDay) }
    var days by remember(e.id) { mutableIntStateOf(e.days) }
    var label by remember(e.id) { mutableStateOf(e.label) }
    var err by remember(e.id) { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (e.id == 0) "Nueva alarma" else "Editar alarma", style = MaterialTheme.typography.titleLarge)
            TimeField("Hora", time, { if (it != null) time = it }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, l ->
                    DayToggle(l, ((days shr i) and 1) == 1, { days = days xor (1 shl i) }, Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                label, { label = it }, label = { Text("Nombre (opcional)") }, singleLine = true,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            CtaButton("Guardar", {
                val t = time
                when {
                    t == null -> err = "Elige la hora."
                    days == 0 -> err = "Elige al menos un día."
                    else -> onSave(e.copy(hour = t / 60, minute = t % 60, days = days, label = label.trim(), enabled = true))
                }
            })
            if (canDelete) SoftButton("Borrar alarma", onDelete, Modifier.fillMaxWidth())
        }
    }
}
