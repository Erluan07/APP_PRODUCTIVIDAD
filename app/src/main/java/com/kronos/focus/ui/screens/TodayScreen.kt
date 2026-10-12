package com.kronos.focus.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.services.AgendaActions
import com.kronos.focus.services.AlarmScheduler
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
    val dateText = remember(now / 3_600_000L) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date(now)).replaceFirstChar { it.uppercase() }
    }

    ScreenScaffold("Hoy", dateText) {
        if (active) {
            HeroCard {
                StatusPill(if (sleeping) "MODO DORMIR" else "MODO BÚNKER", Tone.INFO)
                Text(fmtCountdown(remainingSec), style = MaterialTheme.typography.displayMedium, color = Color.White)
                Text(
                    if (sleeping) "Permitido: Teléfono y Spotify hasta tu alarma." else "Permitido: Teléfono, WhatsApp, Spotify, Mensajes y Contactos.",
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
            AppCard {
                SectionTitle("Sin restricciones ahora", "Los bloques de tu agenda activan el búnker solos.")
                CtaButton("Iniciar búnker de $TEST_BUNKER_MIN min", enabled = a11y, onClick = {
                    store.startSession(TEST_BUNKER_MIN)
                    BunkerWatchdogService.start(ctx)
                })
                if (!a11y) MutedText("Primero activa el servicio de bloqueo.")
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

        val next = AlarmScheduler.nextDeadline(now, AlarmStateStore.get(ctx).lastRingDay == AlarmScheduler.epochDay())
        AppCard {
            IconText(Icons.Filled.Notifications, "Próxima alarma: " + SimpleDateFormat("EEEE HH:mm", Locale("es")).format(Date(next)))
            MutedText("Despertar inteligente: hasta ${SleepLogStore.get(ctx).params().windowMin} min antes si estás en sueño ligero.")
        }

        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Agenda de hoy")
                TextAction("Agregar", { showAdd = true })
            }
            val blocks = agenda.blocks()
            if (blocks.isEmpty()) MutedText("Aún no hay agenda confirmada. Se arma en el ritual matutino.")
            blocks.forEach { b ->
                val proved = remember(b.name, now / 3000) { proofs.hasProofToday(b.name) }
                val (label, tone) = when {
                    proved -> "Con foto" to Tone.OK
                    now >= b.endMs -> "Falta foto" to Tone.WARN
                    now >= b.startMs -> "En curso" to Tone.INFO
                    else -> "Pendiente" to Tone.INFO
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        MutedText("${fmtHm(b.startMs)} – ${fmtHm(b.endMs)}")
                    }
                    StatusPill(label, tone)
                    if (!proved && now >= b.startMs) TextAction("Foto", { actions.openProof(b.name, ProofActivity.KIND_BLOCK) })
                }
            }
        }

        val flex = agenda.flex()
        AppCard {
            SectionTitle("Pendientes sin hora", "Tú decides cuándo empezar. El búnker no se cancela.")
            if (flex.isEmpty()) MutedText("Ninguno.")
            flex.forEach { f ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (f.done) StatusPill("Hecha", Tone.OK)
                    }
                    if (!f.done) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftButton("Empezar", { picking = f.name }, Modifier.weight(1f), enabled = !active)
                            SoftButton("Hecha (foto)", { actions.openProof(f.name, ProofActivity.KIND_FLEX) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        val free = AppRulesConfig.FREE_WINDOWS.joinToString { "%02d:%02d–%02d:%02d".format(it.first / 60, it.first % 60, it.second / 60, it.second % 60) }
        MutedText("Pausa fija sin búnker: $free")
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Agregar a hoy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedText("Solo se pueden agregar tareas; las ya confirmadas no se quitan.")
                    TaskForm("Agregar a mi día", onSubmit = { n, s, e ->
                        if (s == null || e == null) AgendaActions.addFlex(ctx, n)
                        else AgendaActions.addBlock(ctx, AgendaBlock(n, AgendaValidator.todayAt(s), AgendaValidator.todayAt(e)))
                    }, onSuccess = { showAdd = false })
                }
            },
            confirmButton = { TextAction("Cerrar", { showAdd = false }) },
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
                            picking = null
                        }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextAction("Cancelar", { picking = null }) },
        )
    }
}
