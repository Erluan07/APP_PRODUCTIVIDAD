package com.kronos.focus

import android.content.Context
import java.io.File
import java.util.Date

/** Guarda el último error que cerró la app para poder verlo en la pantalla Hoy y copiarlo. Solo texto local. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(ctx).writeText("${Date()} · hilo ${thread.name} · proceso ${android.os.Process.myPid()}\n" + error.stackTraceToString().take(6000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun last(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}
