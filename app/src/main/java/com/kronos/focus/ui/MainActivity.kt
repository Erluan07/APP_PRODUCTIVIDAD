package com.kronos.focus.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.ClipboardManager
import android.widget.Toast
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.data.TaskManagerClient
import com.kronos.focus.domain.agenda.TaskImport
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
import com.kronos.focus.data.ProofStore
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
    private val importLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            runCatching { contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                ?.let { importText(it) } ?: Toast.makeText(this, "No pude leer el archivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun importText(raw: String) {
        val names = TaskImport.parse(raw)
        val added = TemplateStore.get(this).importNames(names)
        Toast.makeText(this, "Importadas $added de ${names.size} tareas", Toast.LENGTH_LONG).show()
    }

    private fun pasteTasks() {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        importText(clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty())
    }

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
            val proofs = ProofStore.get(this@MainActivity)
            agenda.forEach {
                val proved = remember(it.name, now / 3000) { proofs.hasProofToday(it.name) }
                val state = when {
                    proved -> "✔ con foto"
                    now >= it.endMs -> "falta foto"
                    now >= it.startMs -> "▶ búnker"
                    else -> "⏳"
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${agendaFmt.format(Date(it.startMs))}–${agendaFmt.format(Date(it.endMs))}  ${it.name}  $state", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (!proved && now >= it.startMs) {
                        TextButton(onClick = { startActivity(ProofActivity.intent(this@MainActivity, it.name, ProofActivity.KIND_BLOCK)) }) { Text("📷 Foto") }
                    }
                }
            }
            val total = remember(now / 5000) { proofs.count() }
            val intact = remember(now / 10000) { proofs.verify() }
            Text("Evidencias: $total · registro ${if (intact) "íntegro ✔" else "ALTERADO ✖"}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            val tm = TaskManagerClient.pending(this@MainActivity)
            Text(when (tm) {
                is TaskManagerClient.Result.Ok -> "Task Manager conectado: ${tm.tasks.size} tareas pendientes."
                TaskManagerClient.Result.NotAvailable -> "Task Manager: no conectado (instala la versión con el proveedor)."
            }, style = MaterialTheme.typography.bodySmall)
            TasksSection(
                ctx = this@MainActivity, now = now, bunkerActive = store.isActive(now),
                onImportFile = { importLauncher.launch(arrayOf("*/*")) },
                onPaste = { pasteTasks() },
                onProof = { task, kind -> startActivity(ProofActivity.intent(this@MainActivity, task, kind)) },
                onStartTask = { min ->
                    store.startSession(min)
                    BunkerWatchdogService.start(this@MainActivity)
                },
            )
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
