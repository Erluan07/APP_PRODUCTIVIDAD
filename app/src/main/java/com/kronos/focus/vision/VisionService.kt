package com.kronos.focus.vision

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Messenger
import android.os.Process
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.kronos.focus.domain.vision.VerdictParser
import java.io.File

/**
 * Analiza una foto con el modelo local. Corre en su PROPIO proceso (":vision"): si el motor nativo se cae,
 * solo muere este proceso y Kronos (bloqueo, alarma) sigue funcionando. El motor se mantiene cargado mientras
 * la pantalla de evidencia esté abierta y se libera al cerrarse.
 */
class VisionService : Service() {
    private val thread = HandlerThread("kronos-vision").apply { start() }
    private var engine: Engine? = null
    private var engineMode = -1

    private val handler = object : Handler(thread.looper) {
        override fun handleMessage(msg: android.os.Message) {
            if (msg.what != MSG_VERIFY) return
            val d = msg.data
            val out = runCatching {
                verify(d.getString(K_MODEL)!!, d.getString(K_IMAGE)!!, d.getString(K_TASK)!!, d.getInt(K_MODE))
            }.getOrElse { Result(-1, "${it.javaClass.simpleName}: ${it.message}") }
            val reply = android.os.Message.obtain(null, MSG_RESULT).apply {
                data = Bundle().apply { putInt(K_VERDICT, out.verdict); putString(K_REASON, out.reason) }
            }
            runCatching { msg.replyTo?.send(reply) }
        }
    }
    private val messenger = Messenger(handler)

    private data class Result(val verdict: Int, val reason: String)

    private fun ensureEngine(modelPath: String, mode: Int): Engine {
        engine?.let { if (engineMode == mode) return it }
        engine?.close(); engine = null
        // mode 0: GPU para todo (rápido). mode 1 (modo seguro tras un cierre inesperado): CPU principal + visión por GPU.
        val cfg = EngineConfig(
            modelPath = modelPath,
            backend = if (mode == 0) Backend.GPU() else Backend.CPU(),
            visionBackend = Backend.GPU(),
            cacheDir = cacheDir.path,
        )
        return Engine(cfg).also { it.initialize(); engine = it; engineMode = mode }
    }

    /** Reduce la foto (la cámara da 12+ MP; el modelo no los necesita y tardaría mucho más). */
    private fun prepareImage(path: String): String {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("No pude leer la foto")
        val scale = 896f / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = File(cacheDir, "vision_in.jpg")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return out.path
    }

    private fun verify(modelPath: String, imagePath: String, task: String, mode: Int): Result {
        val eng = ensureEngine(modelPath, mode)
        val img = prepareImage(imagePath)
        // Conversación nueva por foto: no arrastra contexto de intentos anteriores.
        val conv = eng.createConversation(ConversationConfig(samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.2)))
        try {
            val reply = conv.sendMessage(Contents.of(Content.ImageFile(img), Content.Text(VerdictParser.prompt(task))))
            val text = reply.toString().trim() // Message.toString() = texto de la respuesta (no existe .text en esta versión)
            val verdict = VerdictParser.parse(text)
            return Result(if (verdict == true) 1 else 0, if (text.isBlank()) "Sin respuesta del modelo" else text.take(300))
        } finally {
            conv.close()
        }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        runCatching { engine?.close() }
        thread.quitSafely()
        super.onDestroy()
        Process.killProcess(Process.myPid()) // libera los GB del modelo
    }

    companion object {
        const val MSG_VERIFY = 1
        const val MSG_RESULT = 2
        const val K_MODEL = "model"; const val K_IMAGE = "image"; const val K_TASK = "task"; const val K_MODE = "mode"
        const val K_VERDICT = "verdict"; const val K_REASON = "reason"
    }
}
