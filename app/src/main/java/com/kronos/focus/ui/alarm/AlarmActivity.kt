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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmFlow
import kotlinx.coroutines.delay

/** Pantalla de alarma: sin botón de descartar/posponer. Solo se sale escaneando el código físico. */
class AlarmActivity : ComponentActivity() {
    private lateinit var store: AlarmStateStore
    private var camGranted by mutableStateOf(false)
    private val camLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        if (!camGranted) camLauncher.launch(Manifest.permission.CAMERA)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Content() } } }
    }

    @Composable
    private fun Content() {
        var phase by remember { mutableStateOf(store.phase) }
        var remaining by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                phase = store.phase
                remaining = ((store.checkDeadlineMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                if (!store.uiRequired()) { finish(); break }
                delay(250)
            }
        }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (phase == AlarmPhase.CHECK_ACTIVE) {
                Text("¿SIGUES DESPIERTO?", style = MaterialTheme.typography.headlineLarge)
                Text("$remaining s", style = MaterialTheme.typography.displayLarge)
                Button(onClick = { AlarmFlow.confirmAwake(this@AlarmActivity) }, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                    Text("ESTOY DESPIERTO", style = MaterialTheme.typography.headlineSmall)
                }
                Text("Si no respondes, la alarma vuelve a sonar.")
            } else {
                Text("¡DESPIERTA!", style = MaterialTheme.typography.headlineLarge)
                Text("Levántate y escanea el código del baño/cocina, o acerca la etiqueta NFC.")
                if (camGranted) {
                    Scanner(Modifier.fillMaxWidth().weight(1f)) { value -> onCode(value) }
                } else {
                    Text("Falta el permiso de cámara: concédelo para escanear.")
                    Button(onClick = { camLauncher.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso de cámara") }
                }
            }
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
