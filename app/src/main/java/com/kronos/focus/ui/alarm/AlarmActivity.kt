package com.kronos.focus.ui.alarm

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import com.kronos.focus.domain.agenda.AgendaBlock
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kronos.focus.data.CalendarReader
import com.kronos.focus.ui.TimeField
import com.kronos.focus.ui.components.*
import com.kronos.focus.ui.theme.KronosTheme
import com.kronos.focus.ui.theme.Palette
import com.kronos.focus.domain.agenda.AgendaValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.data.TemplateStore
import com.kronos.focus.data.TaskManagerClient
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmFlow
import kotlinx.coroutines.delay

/** Pantalla de alarma: sin botón de descartar/posponer. Solo se sale escaneando el código físico. */
class AlarmActivity : ComponentActivity() {
    private lateinit var store: AlarmStateStore
    private var camGranted by mutableStateOf(false)
    private var calGranted by mutableStateOf(false)
    private val calLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { calGranted = it }
    private val camLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = AlarmStateStore.get(this)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        camGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        calGranted = CalendarReader.hasPermission(this)
        if (!camGranted) camLauncher.launch(Manifest.permission.CAMERA)
        setContent { KronosTheme { Content() } }
    }

    @Composable
    private fun Content() {
        var phase by remember { mutableStateOf(store.phase) }
        var remaining by remember { mutableLongStateOf(0L) }
        var muteLeft by remember { mutableLongStateOf(0L) }
        var ritualLeft by remember { mutableLongStateOf(0L) }
        var note by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                phase = store.phase
                note = store.ringNote
                ritualLeft = ((store.ritualDeadlineMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                muteLeft = ((store.muteUntilMs - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)
                remaining = ((store.checkDeadlineMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                if (!store.uiRequired()) { finish(); break }
                delay(250)
            }
        }
        if (phase == AlarmPhase.RITUAL) { Ritual(ritualLeft); return }
        AlarmBackdrop {
            if (phase == AlarmPhase.CHECK_ACTIVE) {
                Spacer(Modifier.weight(1f))
                Text("¿Sigues despierto?", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text("$remaining", style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp), color = Color.White)
                Text("segundos para responder", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                CtaButton("Estoy despierto", { AlarmFlow.confirmAwake(this@AlarmActivity) }, Modifier.height(72.dp))
                Text("Si no respondes, la alarma vuelve a sonar.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            } else {
                Text("¡Despierta!", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Text(
                    "Levántate y escanea el código del baño o la cocina, o acerca la etiqueta NFC.",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f),
                )
                if (muteLeft > 0) {
                    Text(
                        "Silencio: $muteLeft s para escanear. Si no escaneas, vuelve el ruido.",
                        style = MaterialTheme.typography.titleMedium, color = Color.White,
                    )
                } else {
                    OnDarkButton("Ya desperté, voy a escanear (silencio ${AppRulesConfig.MUTE_SECONDS} s)") { AlarmFlow.muteForScan(this@AlarmActivity) }
                }
                if (camGranted) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    ) { Scanner(Modifier.fillMaxSize()) { value -> onCode(value) } }
                } else {
                    Text("Falta el permiso de cámara: concédelo para escanear.", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    CtaButton("Dar permiso de cámara", { camLauncher.launch(Manifest.permission.CAMERA) })
                }
            }
        }
    }

    /** Fondo de la alarma: degradado teal oscuro de la marca, texto blanco de alto contraste. */
    @Composable
    private fun AlarmBackdrop(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Palette.Teal950, Palette.Teal800)))
                .statusBarsPadding().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
    }

    @Composable
    private fun OnDarkButton(text: String, onClick: () -> Unit) {
        OutlinedButton(
            onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.5.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    }

    /** Misión 2: Compromiso Diario. Sin tareas no hay salida; al agotarse el tiempo vuelve la alarma. */
    @Composable
    private fun Ritual(secondsLeft: Long) {
        val blocks = remember { mutableStateListOf<AgendaBlock>() }
        val flex = remember { mutableStateListOf<String>() }
        val templates = remember { TemplateStore.get(this@AlarmActivity).all() }
        val external = remember { TaskManagerClient.pending(this@AlarmActivity) }
        var name by remember { mutableStateOf("") }
        var start by remember { mutableStateOf<Int?>(null) }
        var end by remember { mutableStateOf<Int?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var warn by remember { mutableStateOf<String?>(null) }
        val events = remember(calGranted) { CalendarReader.eventsForDay(this@AlarmActivity) }
        val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Compromiso diario", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            StatusPill("Te quedan ${secondsLeft / 60}:${"%02d".format(secondsLeft % 60)}", if (secondsLeft < 120) Tone.WARN else Tone.INFO)
            MutedText("Elige tus tareas del día. Las que tienen hora activan el Modo Búnker solas; las que no, las inicias tú. Si se acaba el tiempo, vuelve la alarma.")

            if (calGranted) {
                AppCard {
                    SectionTitle("Tu calendario de hoy", "Para que veas si tienes reuniones antes de planear.")
                    if (events.isEmpty()) MutedText("Sin eventos hoy.")
                    events.forEach { ev ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ev.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            MutedText(if (ev.allDay) "Todo el día" else "${fmt.format(Date(ev.startMs))} – ${fmt.format(Date(ev.endMs))}")
                        }
                    }
                }
            } else {
                AppCard {
                    SectionTitle("Calendario", "Permítelo para ver tus reuniones aquí.")
                    SoftButton("Permitir calendario", { calLauncher.launch(Manifest.permission.READ_CALENDAR) }, Modifier.fillMaxWidth())
                }
            }

            AppCard {
                SectionTitle("Tu día")
                if (blocks.isEmpty() && flex.isEmpty()) MutedText("Aún no agregas nada.")
                blocks.sortedBy { it.startMs }.forEach { b ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            MutedText("${fmt.format(Date(b.startMs))} – ${fmt.format(Date(b.endMs))}")
                        }
                        TextAction("Quitar", { blocks.remove(b) })
                    }
                }
                flex.toList().forEach { n ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            MutedText("Sin hora")
                        }
                        TextAction("Quitar", { flex.remove(n) })
                    }
                }
            }

            when (external) {
                is TaskManagerClient.Result.Ok -> if (external.tasks.isNotEmpty()) {
                    AppCard {
                        SectionTitle("De Task Manager", "Pendientes de tu app de tareas")
                        external.tasks.take(30).forEach { t ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                    MutedText("Prioridad ${t.priority}" + (t.dueDate?.let { " · $it" } ?: ""))
                                }
                                TextAction("Usar", { name = t.title; start = null; end = null; error = null })
                            }
                        }
                    }
                }
                TaskManagerClient.Result.NotAvailable -> MutedText("Task Manager no disponible (instala o actualiza esa app).")
            }

            if (templates.isNotEmpty()) {
                AppCard {
                    SectionTitle("Mis tareas", "Toca Usar y ajusta la hora si hace falta.")
                    templates.forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                MutedText(if (t.isTimed) "${t.hm(t.startMin!!)} – ${t.hm(t.endMin!!)}" else "Sin hora")
                            }
                            TextAction("Usar", { name = t.name; start = t.startMin; end = t.endMin; error = null })
                        }
                    }
                }
            }

            AppCard {
                SectionTitle("Agregar a mi día")
                OutlinedTextField(
                    name, { name = it }, label = { Text("Nombre de la tarea") }, singleLine = true,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField("Inicio", start, { start = it }, Modifier.weight(1f))
                    TimeField("Fin", end, { end = it }, Modifier.weight(1f))
                }
                MutedText("Deja las horas en --:-- (o toca Sin hora) si no sabes cuándo la harás.")
                SoftButton("Agregar a mi día", {
                    val s = start
                    val e = end
                    when {
                        name.isBlank() -> error = "Escribe el nombre de la tarea."
                        s == null && e == null -> {
                            if (name.trim() !in flex) flex.add(name.trim())
                            name = ""; error = null
                        }
                        s == null || e == null -> error = "Pon inicio y fin, o deja ambos sin hora."
                        e <= s -> error = "El fin debe ser posterior al inicio."
                        else -> {
                            val b = AgendaBlock(name.trim(), AgendaValidator.todayAt(s), AgendaValidator.todayAt(e))
                            val clash = CalendarReader.conflicts(events, b.startMs, b.endMs).firstOrNull()
                            warn = clash?.let { "Ojo: choca con “${it.title}” (${fmt.format(Date(it.startMs))} – ${fmt.format(Date(it.endMs))})." }
                            blocks.add(b)
                            name = ""; start = null; end = null; error = null
                        }
                    }
                }, Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                warn?.let { StatusPill(it, Tone.WARN) }
            }

            CtaButton(
                "Confirmar compromiso", enabled = blocks.isNotEmpty() || flex.isNotEmpty(),
                onClick = { error = AlarmFlow.commitAgenda(this@AlarmActivity, blocks.toList(), flex.toList()) },
                modifier = Modifier.height(64.dp),
            )
            MutedText("Una vez confirmado no se puede cambiar; solo podrás agregar más tareas durante el día.")
        }
    }

    private fun onCode(value: String) {
        if (value.trim() == AppRulesConfig.ALARM_QR_VALUE) AlarmFlow.scanSuccess(this)
    }

    @OptIn(ExperimentalGetImage::class)
    @Composable
    private fun Scanner(modifier: Modifier, onValue: (String) -> Unit) {
        val owner = LocalLifecycleOwner.current
        AndroidView(modifier = modifier, factory = { c ->
            val view = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val scanner = BarcodeScanning.getClient()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { proxy ->
                    val img = proxy.image
                    if (img == null) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { list -> list.firstNotNullOfOrNull { it.rawValue }?.let(onValue) }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(c))
            view
        })
    }

    // ---- NFC ----
    private fun nfcMatches(tag: Tag): Boolean {
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        if (uid in AppRulesConfig.NFC_UIDS) return true
        val msg = Ndef.get(tag)?.cachedNdefMessage ?: return false
        return msg.records.any { r ->
            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) && r.payload.isNotEmpty() &&
                String(r.payload, (r.payload[0].toInt() and 0x3F) + 1, r.payload.size - 1 - (r.payload[0].toInt() and 0x3F), Charsets.UTF_8) == AppRulesConfig.ALARM_QR_VALUE
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        NfcAdapter.getDefaultAdapter(this)?.enableReaderMode(
            this,
            { tag -> if (nfcMatches(tag)) runOnUiThread { AlarmFlow.scanSuccess(this) } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        visible = false
        runCatching { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) }
        super.onPause()
    }

    /** Los botones de volumen no hacen nada mientras suena. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) true
        else super.onKeyDown(keyCode, event)

    companion object { @Volatile var visible = false }
}
