package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.alarm.DanceQr
import java.security.SecureRandom

/** Clave compartida con el programa del PC para el QR bailable. Se crea sola la primera vez. */
class DanceKeyStore private constructor(private val prefs: SharedPreferences) {

    fun secret(): ByteArray = (prefs.getString("secret", null)?.let { DanceQr.textToSecret(it) }
        ?.takeIf { it.size == DanceQr.SECRET_BYTES }) ?: renew()

    /** Texto para copiar al PC (ABCD-EFGH-…). */
    fun text(): String = DanceQr.secretToText(secret())

    /** Clave nueva: el PC deja de valer hasta que copies la nueva. */
    fun renew(): ByteArray {
        val s = ByteArray(DanceQr.SECRET_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("secret", DanceQr.secretToText(s)).apply()
        return s
    }

    companion object {
        @Volatile private var instance: DanceKeyStore? = null
        fun get(context: Context): DanceKeyStore = instance ?: synchronized(this) {
            instance ?: DanceKeyStore(
                context.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences("dance_key", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
