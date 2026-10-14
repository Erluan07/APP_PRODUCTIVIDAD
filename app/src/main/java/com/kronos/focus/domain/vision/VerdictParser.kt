package com.kronos.focus.domain.vision

import java.text.Normalizer

/** Interpreta la respuesta del modelo ("Sí, se ve…" / "No, es un…"). Puro y probado sin Android. */
object VerdictParser {
    private val YES = Regex("\\b(si|yes)\\b")
    private val NO = Regex("\\bno\\b")

    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** true = sí, false = no, null = no se entiende. Decide la primera de las dos palabras que aparezca. */
    fun parse(text: String): Boolean? {
        val t = normalize(text)
        val yes = YES.find(t)?.range?.first
        val no = NO.find(t)?.range?.first
        return when {
            yes == null && no == null -> null
            yes == null -> false
            no == null -> true
            else -> yes < no
        }
    }

    /** Prompt para el modelo. Respuesta pedida: "SI" o "NO" y un motivo corto. */
    fun prompt(task: String): String =
        "Eres un verificador estricto de tareas. La tarea de la persona es: \"$task\". " +
            "Mira la foto. ¿Se ve claramente a la persona haciendo esa tarea ahora mismo, o en el lugar donde se hace? " +
            "Responde SOLO con la palabra SI o NO al principio, y después una frase corta con el motivo."
}
