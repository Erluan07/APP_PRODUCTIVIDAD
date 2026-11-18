package com.kronos.focus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Botón de hora: al tocarlo se despliega un selector de reloj (24 h). "Sin hora" deja el valor vacío. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minutes: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true }, modifier = modifier.heightIn(min = 52.dp), shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            if (minutes == null) "$label  --:--" else "$label  %02d:%02d".format(minutes / 60, minutes % 60),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (open) {
        val base = minutes ?: (8 * 60)
        val state = rememberTimePickerState(initialHour = base / 60, initialMinute = base % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onChange(null); open = false }) { Text("Sin hora") }
                    TextButton(onClick = { open = false }) { Text("Cancelar") }
                }
            },
        )
    }
}
