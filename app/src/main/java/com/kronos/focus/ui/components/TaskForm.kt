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
