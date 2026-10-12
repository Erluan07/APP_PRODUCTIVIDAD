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
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.ProofStore
import com.kronos.focus.data.StatsStore
import com.kronos.focus.services.AlarmScheduler
import java.io.File

/**
 * Foto de evidencia (Proof of Work). Solo captura en vivo con la cámara (no hay selector de galería).
 * La foto se guarda en el almacenamiento privado de la app y se registra en la cadena inmutable.
 */
class ProofActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var camGranted by mutableStateOf(false)
    private val camLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val task = intent.getStringExtra(EXTRA_TASK).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND).orEmpty()
        camGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!camGranted) camLauncher.launch(Manifest.permission.CAMERA)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Evidencia: $task", style = MaterialTheme.typography.titleLarge)
                        Text("Toma una foto en vivo que muestre que la hiciste. No se puede elegir de la galería.")
                        if (camGranted) {
                            Preview(Modifier.fillMaxWidth().weight(1f))
                            Button(onClick = { capture(task, kind) }, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("TOMAR FOTO") }
                        } else {
                            Text("Falta el permiso de cámara.")
                            Button(onClick = { camLauncher.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso de cámara") }
                        }
                    }
                }
            }
        }
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
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }, ContextCompat.getMainExecutor(c))
            view
        })
    }

    private fun capture(task: String, kind: String) {
        val cap = imageCapture ?: return
        val file = File(ProofStore.proofDir(this), "${System.currentTimeMillis()}.jpg")
        cap.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (ProofStore.get(this@ProofActivity).append(task, kind, file)) {
                        if (kind == KIND_FLEX) AgendaStore.get(this@ProofActivity).setFlexDone(task, true)
                        StatsStore.get(this@ProofActivity).recordDone(AlarmScheduler.epochDay())
                        Toast.makeText(this@ProofActivity, "Evidencia guardada ✔", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        file.delete()
                        Toast.makeText(this@ProofActivity, "No se pudo registrar la evidencia", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(this@ProofActivity, "Error de cámara: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    companion object {
        const val EXTRA_TASK = "task"
        const val EXTRA_KIND = "kind"
        const val KIND_FLEX = "flex"
        const val KIND_BLOCK = "block"
        fun intent(ctx: Context, task: String, kind: String) =
            Intent(ctx, ProofActivity::class.java).putExtra(EXTRA_TASK, task).putExtra(EXTRA_KIND, kind)
    }
}
