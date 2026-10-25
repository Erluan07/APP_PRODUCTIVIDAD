package com.kronos.focus.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.kronos.focus.ui.components.CtaButton
import com.kronos.focus.ui.components.MutedText
import com.kronos.focus.ui.components.StatusPill
import com.kronos.focus.ui.components.Tone
import com.kronos.focus.ui.theme.KronosTheme
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.ProofStore
import com.kronos.focus.data.RoutineStore
import com.kronos.focus.services.AlarmFlow
import com.kronos.focus.data.StatsStore
import com.kronos.focus.services.AlarmScheduler
import java.io.File
import androidx.lifecycle.lifecycleScope
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.ui.components.SoftButton
import com.kronos.focus.vision.VisionClient
import com.kronos.focus.vision.VisionModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Foto de evidencia (Proof of Work). Solo captura en vivo con la cámara (no hay selector de galería).
 * La foto se guarda en el almacenamiento privado de la app y se registra en la cadena inmutable.
 */
class ProofActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var camGranted by mutableStateOf(false)
    private var useFront by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var info by mutableStateOf<String?>(null)
    private val camLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }
    private val client by lazy { VisionClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val task = intent.getStringExtra(EXTRA_TASK).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND).orEmpty()
        camGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!camGranted) camLauncher.launch(Manifest.permission.CAMERA)
        val verifying = VisionModel.isReady(this) && (kind == KIND_ROUTINE ||
            kind == KIND_MUST && AgendaStore.get(this).musts().firstOrNull { !it.done && it.name == task }?.verify == true)
        setContent {
            KronosTheme {
                val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Foto de evidencia", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                        StatusPill(task, Tone.INFO)
                        MutedText(
                            if (verifying) "Toma una foto en vivo que muestre que haces la tarea. La IA del teléfono la revisa; puedes intentar las veces que necesites."
                            else "Toma una foto en vivo que muestre que la hiciste. No se puede elegir de la galería."
                        )
                        info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) }
                        if (camGranted) {
                            key(useFront) { Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))) { Preview(Modifier.fillMaxSize()) } }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SoftButton(if (useFront) "Cámara trasera" else "Cámara frontal", { useFront = !useFront }, Modifier.weight(1f), enabled = !busy)
                            }
                            CtaButton(
                                if (busy) "Analizando…" else "Tomar foto", { capture(task, kind, verifying) },
                                Modifier.height(64.dp), enabled = !busy,
                            )
                        } else {
                            MutedText("Falta el permiso de cámara.")
                            CtaButton("Dar permiso de cámara", { camLauncher.launch(Manifest.permission.CAMERA) })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); visible = true }
    override fun onPause() { visible = false; super.onPause() }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }

    @Composable
    private fun Preview(modifier: Modifier) {
        val owner = LocalLifecycleOwner.current
        AndroidView(modifier = modifier, factory = { c ->
            val view = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner, if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
                )
            }, ContextCompat.getMainExecutor(c))
            view
        })
    }

    private fun capture(task: String, kind: String, verifying: Boolean) {
        val cap = imageCapture ?: return
        val file = File(ProofStore.proofDir(this), "${System.currentTimeMillis()}.jpg")
        cap.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (!verifying) { accept(task, kind, file, verified = false, note = null); return }
                    busy = true
                    info = "Analizando con IA… la primera vez tarda más (carga el modelo)."
                    lifecycleScope.launch {
                        val r = client.verify(file, task)
                        busy = false
                        when (r.verdict) {
                            1 -> accept(task, kind, file, verified = true, note = r.reason)
                            0 -> {
                                file.delete()
                                info = "La IA no ve la tarea: ${r.reason}\nToma otra foto que muestre mejor la tarea."
                            }
                            // Fallo técnico del analizador: no se castiga al usuario; la evidencia se guarda marcada como "sin verificar".
                            else -> accept(task, kind, file, verified = false, note = "Sin verificar por la IA (${r.reason})")
                        }
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(this@ProofActivity, "Error de cámara: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun accept(task: String, kind: String, file: File, verified: Boolean, note: String?) {
        if (ProofStore.get(this).append(task, kind, file)) {
            if (kind == KIND_FLEX) AgendaStore.get(this).setFlexDone(task, true)
            if (kind == KIND_MUST) AgendaStore.get(this).completeMust(task)
            if (kind == KIND_ROUTINE) {
                RoutineStore.get(this).completeStep(task)
                AlarmFlow.routineStepDone(this)
            }
            StatsStore.get(this).recordDone(AlarmScheduler.epochDay())
            Toast.makeText(this, if (verified) "Verificada por la IA ✔" else (note ?: "Evidencia guardada ✔"), Toast.LENGTH_LONG).show()
            finish()
        } else {
            file.delete()
            Toast.makeText(this, "No se pudo registrar la evidencia", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_TASK = "task"
        const val EXTRA_KIND = "kind"
        const val KIND_FLEX = "flex"
        const val KIND_BLOCK = "block"
        const val KIND_MUST = "must"
        const val KIND_ROUTINE = "routine"
        @Volatile var visible = false
        fun intent(ctx: Context, task: String, kind: String) =
            Intent(ctx, ProofActivity::class.java).putExtra(EXTRA_TASK, task).putExtra(EXTRA_KIND, kind)
    }
}
