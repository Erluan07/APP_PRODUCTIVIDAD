package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.kronos.focus.domain.rules.AppRulesConfig

/** Intentos fallidos de verificación con IA por tarea y bloqueo temporal tras agotarlos. */
class VerificationStore private constructor(private val prefs: SharedPreferences) {
    fun lockedUntil(task: String): Long = prefs.getLong("lock_$task", 0L)
    fun attempts(task: String): Int = prefs.getInt("att_$task", 0)

    /** Registra un rechazo. Devuelve los intentos acumulados; al llegar al máximo bloquea [AppRulesConfig.VERIFY_LOCK_MIN] min. */
    fun recordReject(task: String, now: Long): Int {
        val n = attempts(task) + 1
        val e = prefs.edit()
        if (n >= AppRulesConfig.VERIFY_MAX_ATTEMPTS) {
            e.putInt("att_$task", 0).putLong("lock_$task", now + AppRulesConfig.VERIFY_LOCK_MIN * 60_000L)
        } else e.putInt("att_$task", n)
        e.apply()
        return n
    }

    fun clear(task: String) { prefs.edit().remove("att_$task").remove("lock_$task").apply() }

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
