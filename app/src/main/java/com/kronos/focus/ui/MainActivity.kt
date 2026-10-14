package com.kronos.focus.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.domain.agenda.TaskImport
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.services.SleepMode
import com.kronos.focus.services.SleepTrackService
import com.kronos.focus.ui.theme.KronosTheme

/** Pantalla principal con navegación inferior. No hay ajustes ni forma de relajar las reglas. */
class MainActivity : ComponentActivity() {
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            runCatching { contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                ?.let { importText(it) } ?: Toast.makeText(this, "No pude leer el archivo", Toast.LENGTH_LONG).show()
        }
    }
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        SleepTrackService.start(this, fromUser = true) // con o sin micrófono: sin permiso usa solo movimiento
    }
    private val camLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val calLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val actLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestSteps() }

    /** Sensor de pasos: la anti-recaída exige caminar. Sin este permiso usa el movimiento del teléfono (menos fiable). */
    private fun requestSteps() {
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            actLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
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

    private fun startSleepTracking() {
        val next = AlarmScheduler.nextOccurrence(this)
        if (next == null) {
            Toast.makeText(this, "Primero crea una alarma: el Modo Dormir dura hasta que suene.", Toast.LENGTH_LONG).show()
            return
        }
        SleepMode.begin(this)
        Toast.makeText(this, "Modo Dormir activo: las apps quedan bloqueadas hasta tu alarma.", Toast.LENGTH_LONG).show()
        val needMic = AppRulesConfig.SLEEP_USE_MICROPHONE &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO) else SleepTrackService.start(this, fromUser = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.kronos.focus.ui.theme.UiDesign.load(this)
        // Primero notificaciones y, al contestar, pasos (dos diálogos de permiso a la vez se pisan).
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else requestSteps()
        val actions = AppActions(
            importFile = { importLauncher.launch(arrayOf("*/*")) },
            paste = { pasteTasks() },
            startSleep = { startSleepTracking() },
            openProof = { task, kind -> startActivity(ProofActivity.intent(this, task, kind)) },
            requestCamera = { camLauncher.launch(Manifest.permission.CAMERA) },
            requestCalendar = { calLauncher.launch(Manifest.permission.READ_CALENDAR) },
            requestSteps = { actLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        )
        setContent { KronosTheme { AppRoot(actions) } }
    }
}
