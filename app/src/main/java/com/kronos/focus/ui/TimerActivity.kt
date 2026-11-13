package com.kronos.focus.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.ui.components.*
import com.kronos.focus.ui.screens.fmtCountdown
import com.kronos.focus.ui.screens.fmtHm
import com.kronos.focus.ui.theme.KronosTheme
import kotlinx.coroutines.delay

/**
 * Cronómetro en pantalla completa y horizontal (para dejar el teléfono de lado sobre el escritorio).
 * Muestra el tiempo total del búnker y, si hay Pomodoro, la fase, su cuenta atrás y los próximos cambios.
 * Es solo una vista: no cambia ninguna regla. Se cierra con el botón o con "atrás".
 */
class TimerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent { KronosTheme { TimerScreen(onClose = { finish() }) } }
    }

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, TimerActivity::class.java))
    }
}

@Composable
private fun TimerScreen(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BunkerStateStore.get(ctx) }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    val active = store.isActive(now)
    val sleeping = store.isSleepMode(now)
    val remainingSec = store.remainingMs(now) / 1000
    val pomo = if (active && !sleeping) store.pomodoroDetail(now) else null
    val primary = MaterialTheme.colorScheme.primary

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(if (pomo != null) 1.5f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!active) {
                    Text("Sin sesión activa", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        if (sleeping) "MODO DORMIR" else if (pomo != null) (if (pomo.focus) "ENFOQUE" else "DESCANSO · ESTIRA") else "MODO BÚNKER",
                        style = MaterialTheme.typography.titleLarge, color = primary,
                    )
                    Text(
                        fmtCountdown(pomo?.remainingSec ?: remainingSec),
                        fontSize = 132.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (pomo != null) MutedText("Cambia a las ${fmtHm(pomo.nextChangeMs)}")
                    else MutedText(if (sleeping) "hasta tu alarma" else "restante")
                }
            }
            if (pomo != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Tiempo total", fmtCountdown(remainingSec), Modifier.weight(1f))
                        StatTile("Pomodoro", "${pomo.cycle} de ${pomo.totalCycles}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Enfoque", "${com.kronos.focus.domain.rules.AppRulesConfig.POMODORO_FOCUS_MIN} min", Modifier.weight(1f))
                        StatTile("Descanso", "${com.kronos.focus.domain.rules.AppRulesConfig.POMODORO_BREAK_MIN} min", Modifier.weight(1f))
                    }
                    MutedText("En el descanso las apps siguen bloqueadas: levántate, estira y toma agua.")
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopEnd) {
            TextAction("Cerrar", onClose)
        }
    }
}
