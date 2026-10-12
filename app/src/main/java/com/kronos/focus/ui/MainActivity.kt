package com.kronos.focus.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

/** Fase 1: pantalla de estado solo-lectura. Sin ajustes, sin controles para relajar reglas. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Kronos Focus — sin atajos.") }
    }
}
