package com.kronos.focus.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Apps de usuario habilitadas en el Modo Búnker. Se eligen UNA sola vez, la primera vez que se abre Kronos;
 * después de confirmar queda bloqueada (no hay API para cambiarla). Para elegir de nuevo hay que borrar
 * los datos de la app o reinstalarla. El Teléfono y la propia app siempre están permitidos (ver AppRulesConfig).
 */
class AllowedAppsStore private constructor(private val prefs: SharedPreferences) {
    @Volatile private var cache: Set<String>? = null
    @Volatile private var locked: Boolean = prefs.getBoolean(KEY_LOCKED, false)

    fun isLocked(): Boolean = locked

    /** La lista elegida, o null si todavía no se ha elegido (se usan los valores por defecto). */
    fun chosen(): Set<String>? {
        if (!locked) return null
        return cache ?: (prefs.getString(KEY_APPS, "") ?: "").split('\n').filter { it.isNotBlank() }.toSet().also { cache = it }
    }

    /** Guarda la lista y la bloquea para siempre. Ignorada si ya estaba bloqueada. */
    @Synchronized
    fun lock(packages: Set<String>) {
        if (locked) return
        prefs.edit().putString(KEY_APPS, packages.sorted().joinToString("\n")).putBoolean(KEY_LOCKED, true).apply()
        cache = packages; locked = true
    }

    companion object {
        private const val KEY_APPS = "apps"
        private const val KEY_LOCKED = "locked"
        @Volatile private var instance: AllowedAppsStore? = null
        fun get(context: Context): AllowedAppsStore = instance ?: synchronized(this) {
            instance ?: AllowedAppsStore(
                context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences("allowed_apps", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
