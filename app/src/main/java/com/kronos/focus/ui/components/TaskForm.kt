package com.kronos.focus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.ui.TimeField

/** Nombre + hora de inicio/fin (selector de reloj). Sin horas = tarea flexible. [onSubmit] devuelve un error o null. */
@Composable
fun TaskForm(button: String, onSubmit: (String, Int?, Int?) -> String?, onSuccess: () -> Unit = {}) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf<Int?>(null) }
    var end by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            name, { name = it }, label = { Text("Nombre de la tarea") }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField("Inicio", start, { start = it }, Modifier.weight(1f))
            TimeField("Fin", end, { end = it }, Modifier.weight(1f))
        }
        MutedText("Sin hora = tarea flexible (no sabes cuándo ni cuánto).")
        CtaButton(button, onClick = {
            val s = start; val e = end
            val err = when {
                name.isBlank() -> "Escribe el nombre."
                (s == null) != (e == null) -> "Pon inicio y fin, o deja ambos sin hora."
                s != null && e != null && e <= s -> "El fin debe ser posterior al inicio."
                else -> onSubmit(name.trim(), s, e)
            }
            error = err
            if (err == null) { name = ""; start = null; end = null; onSuccess() }
        })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Tarea obligatoria: solo nombre + hora de INICIO. Desde esa hora el teléfono se bloquea hasta subir la evidencia. */
@Composable
fun MustTaskForm(
    onSubmit: (String, Int, Boolean) -> String?, onSuccess: () -> Unit = {},
    button: String = "Agregar tarea obligatoria", confirmFirst: Boolean = true,
) {
    val modelReady = com.kronos.focus.vision.VisionModel.isReady(androidx.compose.ui.platform.LocalContext.current)
    var verify by remember { mutableStateOf(modelReady) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            name, { name = it }, label = { Text("Tarea obligatoria") }, singleLine = true,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
        )
        TimeField("Hora de inicio", start, { start = it }, Modifier.fillMaxWidth())
        MutedText("Sin hora de fin. A partir de la hora de inicio el teléfono se bloquea hasta que la hagas y subas una foto de evidencia. No se puede cancelar.")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Verificar la foto con IA", style = MaterialTheme.typography.titleSmall)
                MutedText(if (modelReady) "La IA del teléfono comprueba que la foto muestra la tarea." else "Primero carga el modelo en Más → Sistema.")
            }
            Switch(checked = verify && modelReady, onCheckedChange = { verify = it }, enabled = modelReady)
        }
        CtaButton(button, onClick = {
            error = when {
                name.isBlank() -> "Escribe el nombre."
                start == null -> "Elige la hora de inicio."
                else -> null
            }
            if (error == null) {
                if (confirmFirst) confirm = true
                else {
                    val err = onSubmit(name.trim(), start!!, verify && modelReady)
                    error = err
                    if (err == null) { name = ""; start = null; onSuccess() }
                }
            }
        })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    if (confirm) {
        val s = start ?: 0
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Confirmar tarea obligatoria?") },
            text = { Text("“${name.trim()}” bloqueará el teléfono desde las %02d:%02d hasta que subas la foto de evidencia. No se puede quitar.".format(s / 60, s % 60)) },
            confirmButton = {
                TextButton(onClick = {
                    val err = onSubmit(name.trim(), s, verify && modelReady)
                    confirm = false
                    error = err
                    if (err == null) { name = ""; start = null; onSuccess() }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Revisar") } },
        )
    }
}
