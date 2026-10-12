package com.kronos.focus.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kronos.focus.BuildConfig
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.services.BunkerWatchdogService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de estado. No hay ajustes ni forma de relajar reglas. El único control inicia un búnker
 * de prueba de duración fija. En builds DEBUG existe además una salida de emergencia para pruebas.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = BunkerStateStore.get(this)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Screen(store) } } }
    }

    @Composable
    private fun Screen(store: BunkerStateStore) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var a11y by remember { mutableStateOf(AccessibilityGuard.isEnabled(this)) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) { now = System.currentTimeMillis(); a11y = AccessibilityGuard.isEnabled(this@MainActivity); delay(1000) }
            }
        }
        val remaining = store.remainingMs(now) / 1000
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kronos Focus", style = MaterialTheme.typography.headlineMedium)
            Text(if (a11y) "Servicio de bloqueo: ACTIVO" else "Servicio de bloqueo: INACTIVO")
            if (!a11y) {
                Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Activar servicio de accesibilidad")
                }
                Text("Si Android lo marca como restringido: Ajustes > Apps > Kronos Focus > ⋮ > Permitir ajustes restringidos.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (!AccessibilityGuard.canSelfHeal(this)) {
                Text("Auto-reactivación OFF. Para activarla (desde el PC): " +
                    "adb shell pm grant com.kronos.focus android.permission.WRITE_SECURE_SETTINGS",
                    style = MaterialTheme.typography.bodySmall)
            }
            val free = AppRulesConfig.FREE_WINDOWS.joinToString { "%02d:%02d–%02d:%02d".format(it.first / 60, it.first % 60, it.second / 60, it.second % 60) }
            Text("Pausa fija sin búnker: $free", style = MaterialTheme.typography.bodySmall)

            if (store.isActive(now)) {
                Text("MODO BÚNKER ACTIVO", style = MaterialTheme.typography.titleLarge)
                Text("Quedan %d:%02d".format(remaining / 60, remaining % 60), style = MaterialTheme.typography.displayMedium)
                Text("Permitido: Teléfono, WhatsApp, Spotify, Mensajes y Contactos.")
            } else {
                Button(enabled = a11y, onClick = {
                    store.startSession(TEST_BUNKER_MIN)
                    BunkerWatchdogService.start(this@MainActivity)
                }) { Text("Iniciar búnker de prueba ($TEST_BUNKER_MIN min)") }
            }

            if (BuildConfig.DEBUG && store.remainingMs(now) > 0) {
                OutlinedButton(onClick = { store.debugClear() }) { Text("Salir del búnker (solo versión de prueba)") }
            }

            HorizontalDivider()
            Text("Intentos bloqueados: ${store.blockedCount()} · Desactivaciones del servicio: ${store.tamperCount()}")
            Text("Últimas apps vistas en búnker (diagnóstico):", style = MaterialTheme.typography.titleSmall)
            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            store.recent().forEach { (t, ok, pkg) ->
                Text("${fmt.format(Date(t))}  ${if (ok) "✔" else "✖"}  $pkg", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    private companion object { const val TEST_BUNKER_MIN = 5L }
}
