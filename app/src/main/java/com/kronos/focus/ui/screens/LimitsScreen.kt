package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.UsageLimitStore
import com.kronos.focus.domain.limits.LimitBlock
import com.kronos.focus.domain.limits.UsageLimits
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MINUTE_CHOICES = listOf(15, 30, 45, 60, 90, 120)

/** Límites de uso: máximo diario por app + corte tras 15 min seguidos (bloqueo de 10 min). Solo se pueden agregar o endurecer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitsSection(ctx: Context, now: Long) {
    val store = remember { UsageLimitStore.get(ctx) }
    var version by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val pm = ctx.packageManager
    fun labelOf(pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    val rows = remember(version, now / 5000L) { store.all().entries.sortedBy { labelOf(it.key).lowercase() } }

    ScreenScaffold(fab = { AddFab("App", Icons.Filled.Add) { adding = true } }) {
        AppCard {
            SectionTitle("Límites de uso", "Elige apps y cuánto puedes usarlas al día.")
            MutedText(
                "• Si pasas ${AppRulesConfig.USAGE_CONTINUOUS_MAX_MIN} min seguidos en una app con límite, te saca y la bloquea ${AppRulesConfig.USAGE_COOLDOWN_MIN} min.\n" +
                    "• Al llegar al máximo diario queda bloqueada hasta mañana.\n" +
                    "• Un límite no se puede quitar ni aumentar; solo bajarlo."
            )
        }
        if (rows.isEmpty()) MutedText("Aún no has puesto límites. Toca “App” para agregar el primero.")
        rows.forEach { (pkg, limit) ->
            val usedMs = store.usedTodayMs(pkg)
            val block = store.blockOf(pkg, now)
            AppCard(Modifier.clickable { editing = pkg }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(labelOf(pkg), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    when (block) {
                        is LimitBlock.Cooldown -> StatusPill("Bloqueada hasta ${fmtHm(block.untilMs)}", Tone.WARN)
                        LimitBlock.DailyExhausted -> StatusPill("Límite de hoy alcanzado", Tone.ERROR)
                        null -> StatusPill("Disponible", Tone.OK)
                    }
                }
                LinearProgressIndicator(progress = { (usedMs / (limit * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                MutedText("Hoy: ${(usedMs / 60_000L)} de $limit min · toca para bajar el límite")
            }
        }
    }

    if (adding) {
        val apps by produceState<List<AppItem>?>(null) { value = withContext(Dispatchers.Default) { installedApps(ctx) } }
        var query by remember { mutableStateOf("") }
        var picked by remember { mutableStateOf<String?>(null) }
        var minutes by remember { mutableStateOf("30") }
        val value = minutes.toIntOrNull()
        val valid = picked != null && value != null && UsageLimits.canSet(store.limitMin(picked!!), value)
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Nuevo límite") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Buscar app") }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                    val list = apps
                    if (list == null) CircularProgressIndicator() else {
                        val shown = remember(list, query) { list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) } }
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            items(shown, key = { it.pkg }) { a ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { picked = a.pkg }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = picked == a.pkg, onClick = { picked = a.pkg })
                                    Text(a.label + if (store.isLimited(a.pkg)) "  (ya tiene límite)" else "", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    MutedText("Máximo al día:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MINUTE_CHOICES.forEach { m ->
                            FilterChip(selected = value == m, onClick = { minutes = m.toString() }, label = { Text(if (m % 60 == 0) "${m / 60} h" else "$m min") })
                        }
                    }
                    OutlinedTextField(
                        minutes, { s -> minutes = s.filter { it.isDigit() }.take(3) }, singleLine = true, isError = value == null || !UsageLimits.canSet(null, value),
                        label = { Text("Minutos (${AppRulesConfig.USAGE_MIN_LIMIT_MIN} a ${AppRulesConfig.USAGE_MAX_LIMIT_MIN})") },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                    )
                    MutedText("Después no podrás quitarlo ni subirlo.")
                }
            },
            confirmButton = { TextAction("Agregar", { if (store.setLimit(picked!!, value!!)) { version++; adding = false } }, enabled = valid) },
            dismissButton = { TextAction("Cancelar", { adding = false }) },
        )
    }

    editing?.let { pkg ->
        val current = store.limitMin(pkg) ?: return@let
        var minutes by remember(pkg) { mutableStateOf((current - 5).coerceAtLeast(AppRulesConfig.USAGE_MIN_LIMIT_MIN).toString()) }
        val value = minutes.toIntOrNull()
        val valid = value != null && value < current && UsageLimits.canSet(current, value)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Bajar límite de ${labelOf(pkg)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MutedText("Ahora: $current min al día. Solo se puede poner un valor menor.")
                    OutlinedTextField(
                        minutes, { s -> minutes = s.filter { it.isDigit() }.take(3) }, singleLine = true, isError = !valid,
                        label = { Text("Nuevo máximo (min)") }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextAction("Guardar", { if (store.setLimit(pkg, value!!)) { version++; editing = null } }, enabled = valid) },
            dismissButton = { TextAction("Cancelar", { editing = null }) },
        )
    }
}
