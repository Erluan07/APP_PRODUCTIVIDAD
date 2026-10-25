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
        "Verificas fotos de evidencia de una tarea personal. La tarea es: \"$task\". " +
            "Responde SI si es razonable que la foto muestre a la persona haciendo esa tarea o recién hecha, aunque no se vea todo " +
            "(por ejemplo, si la tarea es tomar o beber algo, basta ver un vaso, botella o recipiente en la mano o en la boca, " +
            "aunque el líquido sea transparente o incoloro; si es cepillarse, basta el cepillo o la espuma). " +
            "Responde NO solo si la foto claramente no tiene relación con la tarea (pared, techo, cama, pantalla, nada que ver). " +
            "Responde SOLO con la palabra SI o NO al principio, y después una frase corta con el motivo."
}
