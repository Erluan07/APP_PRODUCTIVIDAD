package com.kronos.focus.vision

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** El modelo de visión (archivo .litertlm, p. ej. Gemma 3n E4B) que el usuario carga una vez. Vive en el almacenamiento privado de Kronos. */
object VisionModel {
    private const val MIN_BYTES = 1_000_000_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Estado de la importación para la pantalla: null = nada en curso. */
    var progressPercent by mutableStateOf<Int?>(null)
        private set
    var lastMessage by mutableStateOf<String?>(null)

    fun file(ctx: Context) = File(File(ctx.filesDir, "models").apply { mkdirs() }, "vision.litertlm")

    fun isReady(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() >= MIN_BYTES }

    fun sizeMb(ctx: Context): Long = file(ctx).length() / 1_000_000L

    /** Copia el archivo elegido al almacenamiento de la app (en segundo plano, sin quedarse a medias si falla). */
    fun importFrom(ctx: Context, uri: Uri) {
        if (progressPercent != null) return
        val app = ctx.applicationContext
        progressPercent = 0; lastMessage = null
        scope.launch {
            val tmp = File(file(app).parentFile, "vision.tmp")
            try {
                var total = -1L
                app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) total = c.getLong(0)
                }
                if (total in 1 until MIN_BYTES) throw IllegalStateException("El archivo es demasiado pequeño para ser el modelo.")
                if (total > 0 && StatFs(app.filesDir.path).availableBytes < total + 300_000_000L) {
                    throw IllegalStateException("No hay espacio: hacen falta unos ${(total + 300_000_000L) / 1_000_000_000L + 1} GB libres.")
                }
                app.contentResolver.openInputStream(uri)!!.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; progressPercent = pct }
                            }
                        }
                    }
                }
                if (tmp.length() < MIN_BYTES) throw IllegalStateException("El archivo copiado está incompleto.")
                val dest = file(app)
                dest.delete()
                if (!tmp.renameTo(dest)) throw IllegalStateException("No pude guardar el modelo.")
                lastMessage = "Modelo cargado (${dest.length() / 1_000_000L} MB)."
            } catch (e: Exception) {
                tmp.delete()
                lastMessage = "No se pudo cargar: ${e.message}"
            } finally {
                progressPercent = null
            }
        }
    }
}
