package com.kronos.focus.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/** Al pulsar "Me voy a dormir": rutina de mañana (si la hay) y tipo de QR para apagar la alarma. */
@Composable
fun SleepSetupDialog(steps: List<String>, onConfirm: (routine: Boolean, dance: Boolean) -> Unit, onCancel: () -> Unit) {
    var routine by remember { mutableStateOf(steps.isNotEmpty()) }
    var dance by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Antes de dormir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (steps.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Rutina de mañana", style = MaterialTheme.typography.titleSmall)
                            Text(
                                steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = routine, onCheckedChange = { routine = it })
                    }
                    HorizontalDivider()
                }
                Text("¿Cómo apagas la alarma?", style = MaterialTheme.typography.titleSmall)
                QrOption("QR normal", "El de siempre: escaneas el código del baño o la cocina.", selected = !dance, sparkle = false) { dance = false }
                QrOption(
                    "QR bailable", "Bailas 30 s frente a la cámara del PC y te genera un QR nuevo. El normal no vale.",
                    selected = dance, sparkle = true,
                ) { dance = true }
                if (dance) {
                    Text(
                        "Solo elígelo si el PC va a estar contigo cuando suene la alarma: sin él no podrás apagarla. " +
                            "La clave del PC está en Más → Sistema → QR bailable.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(routine && steps.isNotEmpty(), dance) }) { Text("Me voy a dormir") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

@Composable
private fun QrOption(title: String, detail: String, selected: Boolean, sparkle: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "sparkle")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val sc by t.animateFloat(0.8f, 1.25f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = MaterialTheme.shapes.medium, border = BorderStroke(if (selected) 2.dp else 1.dp, color),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (sparkle) Text("✨", Modifier.alpha(a).scale(sc))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (sparkle) { Text("✨", Modifier.alpha(b).scale(2.05f - sc)); Text("⭐", Modifier.alpha(a * b + 0.2f)) }
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
