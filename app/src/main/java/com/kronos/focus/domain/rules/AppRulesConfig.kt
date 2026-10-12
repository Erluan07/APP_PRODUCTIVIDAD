package com.kronos.focus.domain.rules

/**
 * REGLAS INMUTABLES.
 *
 * No existe UI ni almacenamiento que modifique estos valores. Cambiarlos exige editar este
 * archivo y recompilar/instalar por USB. No añadir NUNCA lectura desde prefs, red ni archivos.
 */
object AppRulesConfig {

    const val APP_PACKAGE = "com.kronos.focus"

    /** Apps de usuario permitidas dentro del Modo Búnker. */
    val WHITELIST: Set<String> = setOf(
        APP_PACKAGE,
        // 1. Teléfono (varía por fabricante; incluye pantalla de llamada en curso)
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        // 2. WhatsApp
        "com.whatsapp",
        "com.whatsapp.w4b",
        // 3. Spotify
        "com.spotify.music",
        // 4. Emergencias: mensajes y contactos (SMS/llamar a un contacto)
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        "com.android.contacts",
    )

    /** Prefijos permitidos (variantes de la misma app: WhatsApp Business, procesos auxiliares). */
    val ALLOWED_PREFIXES: List<String> = listOf("com.whatsapp", "com.spotify")

    /** Cualquier paquete de ajustes se bloquea (cubre OEMs con paquetes distintos). */
    val BLOCKED_PREFIXES: List<String> = listOf(
        "com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter",
    )

    /**
     * Pausa fija (hora local, minutos desde medianoche). El búnker no se aplica en estas ventanas.
     * EDITA AQUÍ tu hora de comer y recompila.
     */
    val FREE_WINDOWS: List<Pair<Int, Int>> = listOf(14 * 60 to 15 * 60)

    /** Una sesión nunca puede pedir más que esto, ni el total pendiente (con penalizaciones) superarlo. */
    const val MAX_SINGLE_BUNKER_MIN = 240L
    const val MAX_TOTAL_BUNKER_MIN = 480L

    /**
     * Infraestructura imprescindible para que el teléfono siga usable (barra de estado,
     * teclado, selector de permisos). NO son "apps de ocio". Los launchers se resuelven
     * en tiempo de ejecución (ver [com.kronos.focus.services.BunkerAccessibilityService]).
     */
    val SYSTEM_ALLOWED: Set<String> = setOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /** Ajustes del sistema y desinstalador: bloqueados en búnker para impedir desactivar el servicio. */
    val ALWAYS_BLOCKED_IN_BUNKER: Set<String> = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    // ---- Parámetros de comportamiento ----
    const val WAKE_CHECK_MIN_DELAY_MIN = 7
    const val WAKE_CHECK_MAX_DELAY_MIN = 10
    const val WAKE_CHECK_COUNTDOWN_SEC = 90
    const val SMART_WAKE_WINDOW_MIN = 25
    const val BOOT_TRAP_PENALTY_MIN = 60L

    /** Nombres de paquete que jamás deben bloquearse (llamadas de emergencia). */
    fun isAllowed(pkg: String, launchers: Set<String>, imes: Set<String>): Boolean =
        pkg !in ALWAYS_BLOCKED_IN_BUNKER &&
            BLOCKED_PREFIXES.none { pkg.startsWith(it) } && !pkg.contains(".settings") &&
            (pkg in WHITELIST || ALLOWED_PREFIXES.any { pkg.startsWith(it) } ||
                pkg in SYSTEM_ALLOWED || pkg in launchers || pkg in imes)
}
