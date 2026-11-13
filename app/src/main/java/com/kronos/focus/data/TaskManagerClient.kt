package com.kronos.focus.data

import android.content.Context
import android.net.Uri

/** Tarea pendiente leída de la app Task Manager IA (com.erluan.taskmanageria). */
data class ExternalTask(val id: Long, val title: String, val priority: String, val dueDate: String?)

/** Lectura (solo lectura, sin internet) del proveedor de datos de la app de tareas. */
object TaskManagerClient {
    private const val AUTHORITY = "com.erluan.taskmanageria.tasks"
    private val URI: Uri = Uri.parse("content://$AUTHORITY/tasks")

    sealed class Result {
        data class Ok(val tasks: List<ExternalTask>) : Result()
        /** La app de tareas no está instalada o aún no tiene el proveedor (versión sin actualizar). */
        object NotAvailable : Result()
    }

    fun isAvailable(ctx: Context): Boolean =
        ctx.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    fun pending(ctx: Context): Result {
        if (!isAvailable(ctx)) return Result.NotAvailable
        return runCatching {
            ctx.contentResolver.query(URI, null, null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow("id")
                val title = c.getColumnIndexOrThrow("title")
                val prio = c.getColumnIndexOrThrow("priority")
                val due = c.getColumnIndexOrThrow("dueDate")
                buildList {
                    while (c.moveToNext()) {
                        val t = c.getString(title)?.trim().orEmpty()
                        if (t.isNotEmpty()) add(ExternalTask(c.getLong(id), t, c.getString(prio) ?: "media", c.getString(due)))
                    }
                }
            }
        }.fold({ Result.Ok(it ?: emptyList()) }, { Result.NotAvailable })
    }
}
