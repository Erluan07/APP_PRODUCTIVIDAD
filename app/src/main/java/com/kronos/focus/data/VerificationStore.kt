package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences

/** Cierres inesperados del analizador de IA (no hay límite de intentos con la foto). */
class VerificationStore private constructor(private val prefs: SharedPreferences) {
    /** Cuántas veces se cerró el analizador de forma inesperada (para pasar a modo seguro por CPU). */
    fun crashes(): Int = prefs.getInt("crashes", 0)
    fun recordCrash() { prefs.edit().putInt("crashes", crashes() + 1).apply() }

    companion object {
        @Volatile private var instance: VerificationStore? = null
        fun get(context: Context): VerificationStore = instance ?: synchronized(this) {
            instance ?: VerificationStore(context.applicationContext.getSharedPreferences("verification", Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}
