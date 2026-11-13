package com.kronos.focus.domain.agenda

import org.json.JSONArray
import org.json.JSONObject

/** Extrae nombres de tareas de un JSON (lista de textos u objetos) o de texto plano (una por línea). */
object TaskImport {
    private val NAME_KEYS = listOf("title", "name", "titulo", "título", "nombre", "task", "tarea", "text", "texto", "content", "description")
    private val LIST_KEYS = listOf("tasks", "tareas", "items", "todos", "data")
    private val DONE_KEYS = listOf("done", "completed", "completada", "hecha", "completado", "isCompleted")

    fun parse(raw: String): List<String> {
        val t = raw.trim().removePrefix("﻿")
        if (t.isEmpty()) return emptyList()
        val arr: JSONArray? = runCatching {
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> JSONObject(t).let { o -> LIST_KEYS.firstNotNullOfOrNull { o.optJSONArray(it) } }
                else -> null
            }
        }.getOrNull()
        val names = if (arr != null) fromJson(arr) else fromLines(t)
        return names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(200)
    }

    private fun fromJson(arr: JSONArray): List<String> = (0 until arr.length()).mapNotNull { i ->
        when (val v = arr.opt(i)) {
            is String -> v
            is JSONObject ->
                if (DONE_KEYS.any { v.optBoolean(it, false) }) null
                else NAME_KEYS.firstNotNullOfOrNull { k -> v.optString(k, "").takeIf { it.isNotBlank() } }
            else -> null
        }
    }

    private fun fromLines(t: String): List<String> = t.lines().mapNotNull { line ->
        val l = line.trim()
        if (Regex("^[-*•]?\\s*\\[[xX]\\]").containsMatchIn(l)) return@mapNotNull null // ya hecha
        l.replace(Regex("^([-*•]|\\d+[.)]|\\[[ ]?\\])\\s*"), "").replace(Regex("^\\[[ ]?\\]\\s*"), "").ifBlank { null }
    }
}
