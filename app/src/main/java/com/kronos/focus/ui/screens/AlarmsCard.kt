package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.AlarmConfigStore
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.alarm.AlarmEntry
import com.kronos.focus.domain.alarm.AlarmTimes
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.ui.TimeField
import com.kronos.focus.ui.components.*

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

/** Alarmas del usuario: hora, días, activar/desactivar. Una alarma que suena pronto queda bloqueada para que no se pueda esquivar. */
@Composable
fun AlarmsCard(ctx: Context, now: Long) {
    val store = AlarmConfigStore.get(ctx)
    val ringed = AlarmStateStore.get(ctx).lastRingStamp
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AlarmEntry?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val alarms = remember(tick, now / 2000) { store.all().sortedBy { it.minutesOfDay } }

    fun save(a: AlarmEntry) { store.upsert(a); AlarmScheduler.scheduleDaily(ctx); tick++ }
    fun lockedMsg() = "No se puede cambiar una alarma que suena en menos de ${AppRulesConfig.ALARM_LOCK_HOURS} h."

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Alarmas", "Elige hora y días. Se bloquean ${AppRulesConfig.ALARM_LOCK_HOURS} h antes de sonar.")
        }
        if (alarms.isEmpty()) MutedText("No tienes alarmas. Agrega una para que Kronos te despierte.")
        alarms.forEach { a ->
            val locked = AlarmTimes.isLocked(a, now, ringed, AppRulesConfig.ALARM_LOCK_HOURS)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.hm(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    MutedText(AlarmTimes.daysText(a.days) + if (a.label.isNotBlank()) " · ${a.label}" else "")
                    if (locked) StatusPill("Bloqueada: suena pronto", Tone.WARN, Modifier.padding(top = 4.dp))
                }
                Switch(
                    checked = a.enabled,
                    onCheckedChange = { on -> if (!on && locked) msg = lockedMsg() else { msg = null; save(a.copy(enabled = on)) } },
                )
                IconButton(onClick = { if (locked) msg = lockedMsg() else { msg = null; editing = a } }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar alarma ${a.hm()}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (locked) msg = lockedMsg() else { msg = null; store.delete(a.id); AlarmScheduler.scheduleDaily(ctx); tick++ } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Borrar alarma ${a.hm()}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        val e = editing
        if (e == null) {
            SoftButton("Agregar alarma", { editing = AlarmEntry(0, 7, 0, AlarmTimes.ALL_DAYS, true, "") }, Modifier.fillMaxWidth())
        } else {
            var time by remember(e.id) { mutableStateOf<Int?>(e.minutesOfDay) }
            var days by remember(e.id) { mutableIntStateOf(e.days) }
            var label by remember(e.id) { mutableStateOf(e.label) }
            var err by remember(e.id) { mutableStateOf<String?>(null) }
            HorizontalDivider()
            SectionTitle(if (e.id == 0) "Nueva alarma" else "Editar alarma")
            TimeField("Hora", time, { if (it != null) time = it }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, l ->
                    DayToggle(l, (days shr i) and 1 == 1, { days = days xor (1 shl i) }, Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                label, { label = it }, label = { Text("Nombre (opcional)") }, singleLine = true,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Cancelar", { editing = null }, Modifier.weight(1f))
                CtaButton("Guardar", {
                    val t = time
                    when {
                        t == null -> err = "Elige la hora."
                        days == 0 -> err = "Elige al menos un día."
                        else -> { save(e.copy(hour = t / 60, minute = t % 60, days = days, label = label.trim(), enabled = true)); editing = null }
                    }
                }, Modifier.weight(1f))
            }
        }
    }
}
