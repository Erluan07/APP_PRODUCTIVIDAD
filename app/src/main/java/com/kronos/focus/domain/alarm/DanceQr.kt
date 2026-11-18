package com.kronos.focus.domain.alarm

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * QR bailable: el programa del PC genera un código que cambia cada minuto y solo lo conoce quien tiene la clave
 * compartida (se copia una vez del teléfono al PC). Una captura de pantalla vieja no sirve: caduca en unos minutos
 * y debe haberse generado DESPUÉS de que empezó a sonar la alarma. Formato: KRONOSD1|<minuto>|<mac de 12 hex>.
 * Lógica pura (sin Android) y probada; el PC (tools/dance-qr/dance-core.js) calcula exactamente lo mismo.
 */
object DanceQr {
    const val PREFIX = "KRONOSD1"
    const val SECRET_BYTES = 20
    /** Cuánto tiempo atrás (en minutos) todavía vale un código. */
    const val VALID_BACK_MIN = 3L
    /** Tolerancia por si el reloj del PC va unos segundos adelantado. */
    const val VALID_AHEAD_MIN = 1L

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun mac(secret: ByteArray, minute: Long): String {
        val m = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        return m.doFinal("kronos-dance|$minute".toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
    }

    fun payload(secret: ByteArray, nowMs: Long): String {
        val minute = nowMs / 60_000L
        return "$PREFIX|$minute|${mac(secret, minute)}"
    }

    /**
     * ¿Es un código válido? [ringStartMs] = cuándo empezó a sonar la alarma: el código tiene que ser de ese minuto o
     * posterior (no se puede generar de antemano).
     */
    fun verify(secret: ByteArray?, text: String, nowMs: Long, ringStartMs: Long): Boolean {
        if (secret == null) return false
        val parts = text.trim().split('|')
        if (parts.size != 3 || parts[0] != PREFIX) return false
        val minute = parts[1].toLongOrNull() ?: return false
        val nowMin = nowMs / 60_000L
        if (minute < nowMin - VALID_BACK_MIN || minute > nowMin + VALID_AHEAD_MIN) return false
        if (minute < ringStartMs / 60_000L) return false
        return MessageDigest.isEqual(parts[2].toByteArray(), mac(secret, minute).toByteArray())
    }

    /** Base32 (RFC 4648, sin relleno) en grupos de 4 para copiarlo a mano: ABCD-EFGH-... */
    fun secretToText(secret: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0; var bits = 0
        for (b in secret) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF); bits += 8
            while (bits >= 5) { sb.append(ALPHABET[(buffer shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString().chunked(4).joinToString("-")
    }

    fun textToSecret(text: String): ByteArray? {
        val clean = text.uppercase().filter { it != '-' && !it.isWhitespace() }
        if (clean.isEmpty() || clean.any { it !in ALPHABET }) return null
        val out = ArrayList<Byte>()
        var buffer = 0; var bits = 0
        for (c in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c); bits += 5
            if (bits >= 8) { out.add(((buffer shr (bits - 8)) and 0xFF).toByte()); bits -= 8 }
        }
        return out.toByteArray()
    }
}
