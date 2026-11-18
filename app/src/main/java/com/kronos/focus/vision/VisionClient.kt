package com.kronos.focus.vision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import com.kronos.focus.data.VerificationStore
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** verdict: 1 = sí, 0 = no, -1 = error técnico (el analizador falló: NO se castiga al usuario). */
data class VisionResult(val verdict: Int, val reason: String)

/** Habla con [VisionService] (otro proceso). Usar desde el hilo principal; [close] al terminar. */
class VisionClient(private val ctx: Context) {
    private var service: Messenger? = null
    private var pending: CancellableContinuation<VisionResult>? = null
    private var connecting: CancellableContinuation<Boolean>? = null

    private val replyTo = Messenger(Handler(Looper.getMainLooper()) { m ->
        if (m.what == VisionService.MSG_RESULT) {
            val b = m.data
            pending?.resume(VisionResult(b.getInt(VisionService.K_VERDICT), b.getString(VisionService.K_REASON).orEmpty()))
            pending = null
        }
        true
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) { service = Messenger(b); connecting?.resume(true); connecting = null }
        override fun onServiceDisconnected(n: ComponentName) {
            // El proceso del analizador murió (fallo nativo): se anota y la próxima vez se usa el modo seguro.
            service = null
            VerificationStore.get(ctx).recordCrash()
            pending?.resume(VisionResult(-1, "El analizador se cerró inesperadamente"))
            pending = null
        }
    }

    private suspend fun connect(): Boolean {
        if (service != null) return true
        return withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Boolean> { c ->
                connecting = c
                if (!ctx.bindService(Intent(ctx, VisionService::class.java), conn, Context.BIND_AUTO_CREATE)) { connecting = null; c.resume(false) }
            }
        } ?: false
    }

    suspend fun verify(image: File, task: String): VisionResult {
        if (!connect()) return VisionResult(-1, "No pude iniciar el analizador")
        val mode = if (VerificationStore.get(ctx).crashes() >= 1) 1 else 0
        return withTimeoutOrNull(300_000L) {
            suspendCancellableCoroutine<VisionResult> { c ->
                pending = c
                val msg = android.os.Message.obtain(null, VisionService.MSG_VERIFY).apply {
                    replyTo = this@VisionClient.replyTo
                    data = Bundle().apply {
                        putString(VisionService.K_MODEL, VisionModel.file(ctx).path)
                        putString(VisionService.K_IMAGE, image.path)
                        putString(VisionService.K_TASK, task)
                        putInt(VisionService.K_MODE, mode)
                    }
                }
                runCatching { service!!.send(msg) }.onFailure { pending = null; c.resume(VisionResult(-1, "No pude enviar la foto al analizador")) }
            }
        } ?: VisionResult(-1, "El análisis tardó demasiado")
    }

    fun close() {
        runCatching { ctx.unbindService(conn) }
        service = null
    }
}
