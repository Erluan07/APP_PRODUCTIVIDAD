package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.services.SleepTrackService
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SleepScreen(ctx: Context, now: Long, actions: AppActions) {
    val sleepLog = SleepLogStore.get(ctx)
    val params = sleepLog.params()
    val tracking = SleepTrackService.running
    val next = AlarmScheduler.nextOccurrence(ctx, now)
    val sleepingNow = com.kronos.focus.data.BunkerStateStore.get(ctx).isSleepMode(now)

    ScreenScaffold("Dormir", "Despertar suave y Modo Dormir") {
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
                StatusPill(if (sleepingNow) "MODO DORMIR" else "REGISTRANDO", Tone.INFO)
                Text(if (tracking) "Registrando tu sueño" else "Modo Dormir activo", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Acelerómetro" + (if (AppRulesConfig.SLEEP_USE_MICROPHONE) " y volumen del ambiente" else "") +
                        ". Deja el teléfono cargando junto a la cama.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
            }
        } else {
            AppCard {
                SectionTitle("Antes de dormir", "Activa el Modo Dormir hasta tu próxima alarma y registra tu sueño para despertarte en sueño ligero.")
                CtaButton("Me voy a dormir", { actions.startSleep() }, icon = Icons.Filled.Notifications)
                MutedText("Deja el teléfono cargando junto a la cama. Del micrófono solo se mide el volumen; no se graba nada.")
            }
        }

        AlarmsCard(ctx, now)

        AppCard {
            IconText(
                Icons.Filled.Notifications,
                if (next == null) "Sin alarmas activas" else "Próxima alarma: " + SimpleDateFormat("EEEE HH:mm", Locale("es")).format(Date(next.timeMs)),
            )
        }

        AppCard {
            SectionTitle("Despertar inteligente", "Se ajusta solo con tu valoración de cada mañana.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Ventana", "${params.windowMin} min", Modifier.weight(1f))
                StatTile("Umbral", "%.2f".format(params.threshold), Modifier.weight(1f))
            }
            MutedText("Combina movimiento, sonido ambiente y el punto del ciclo de 90 min. Es una estimación: un teléfono no mide fases de sueño.")
        }

        AppCard {
            SectionTitle("Modo Dormir", "Lo activas tú con “Me voy a dormir” y termina cuando suena tu alarma.")
            MutedText("Mientras está activo solo se permiten Teléfono y Spotify, para que no te acuestes con el móvil.")
        }
        Spacer(Modifier.height(4.dp))
    }
}
