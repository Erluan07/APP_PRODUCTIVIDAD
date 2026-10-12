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
import com.kronos.focus.services.AlarmFlow
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.AgendaStore
import android.app.NotificationManager
import android.net.Uri
import android.os.PowerManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de estado. No hay ajustes ni forma de relajar reglas. El único control inicia un búnker
 * de prueba de duración fija. En builds DEBUG existe además una salida de emergencia para pruebas.
 */
class MainActivity : ComponentActivity() {
    private val camLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

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
            if (!AccessibilityGuard.canSelfHeal(this@MainActivity)) {
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
            Text("Agenda de hoy (fija tras el ritual)", style = MaterialTheme.typography.titleSmall)
            val agendaFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val agenda = AgendaStore.get(this@MainActivity).blocks()
            if (agenda.isEmpty()) Text("Sin agenda confirmada.", style = MaterialTheme.typography.bodySmall)
            agenda.forEach {
                val state = when { now >= it.endMs -> "✔"; now >= it.startMs -> "▶ búnker"; else -> "⏳" }
                Text("${agendaFmt.format(Date(it.startMs))}–${agendaFmt.format(Date(it.endMs))}  ${it.name}  $state", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Alarma %02d:%02d (ventana Smart Wake-Up: %d min)".format(AppRulesConfig.ALARM_HOUR, AppRulesConfig.ALARM_MINUTE, AppRulesConfig.SMART_WAKE_WINDOW_MIN),
                style = MaterialTheme.typography.titleSmall)
            Text("Código a escanear: ${AppRulesConfig.ALARM_QR_VALUE} · Fase: ${AlarmStateStore.get(this@MainActivity).phase}",
                style = MaterialTheme.typography.bodySmall)
            if (checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Button(onClick = { camLauncher.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso de cámara (necesario para escanear)") }
            }
            if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
                Button(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }) {
                    Text("Permitir pantalla completa de alarma")
                }
            }
            if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
                Button(onClick = { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }) {
                    Text("Excluir de ahorro de batería")
                }
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(onClick = { AlarmFlow.ring(this@MainActivity, test = true) }) { Text("[Prueba] Hacer sonar ahora") }
                OutlinedButton(onClick = { AlarmScheduler.scheduleTest(this@MainActivity, System.currentTimeMillis() + 60_000L) }) { Text("[Prueba] Sonar en 1 minuto (pantalla apagada)") }
                OutlinedButton(onClick = { AlarmFlow.startCheck(this@MainActivity) }) { Text("[Prueba] Wake-Up Check ahora") }
                OutlinedButton(onClick = { AgendaStore.get(this@MainActivity).clear() }) { Text("[Prueba] Borrar agenda de hoy") }
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
