package com.kronos.focus.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.services.BunkerAccessibilityService
import kotlinx.coroutines.delay

/**
 * MVP Fase 1. Sin ajustes ni botón de cancelar. El único control inicia un búnker de prueba
 * de duración fija; solo puede alargar el bloqueo, nunca acortarlo.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = BunkerStateStore.get(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Screen(store) } } }
    }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, BunkerAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    @Composable
    private fun Screen(store: BunkerStateStore) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var a11y by remember { mutableStateOf(accessibilityEnabled()) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) { now = System.currentTimeMillis(); a11y = accessibilityEnabled(); delay(1000) }
            }
        }
        val remaining = store.remainingMs(now) / 1000
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Kronos Focus", style = MaterialTheme.typography.headlineMedium)
            Text(if (a11y) "Servicio de bloqueo: ACTIVO" else "Servicio de bloqueo: INACTIVO")
            if (!a11y) {
                Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Activar servicio de accesibilidad")
                }
                Text("Si Android lo marca como restringido: Ajustes > Apps > Kronos Focus > ⋮ > " +
                    "Permitir ajustes restringidos, y vuelve a intentarlo.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (store.isActive(now)) {
                Text("MODO BÚNKER ACTIVO", style = MaterialTheme.typography.titleLarge)
                Text("Quedan %d:%02d".format(remaining / 60, remaining % 60),
                    style = MaterialTheme.typography.displayMedium)
                Text("Permitido: Teléfono, WhatsApp, Spotify.")
            } else {
                Button(enabled = a11y, onClick = {
                    store.extendUntil(System.currentTimeMillis() + TEST_BUNKER_MIN * 60_000L)
                }) { Text("Iniciar búnker de prueba ($TEST_BUNKER_MIN min, sin cancelar)") }
            }
        }
    }

    private companion object { const val TEST_BUNKER_MIN = 5 }
}
