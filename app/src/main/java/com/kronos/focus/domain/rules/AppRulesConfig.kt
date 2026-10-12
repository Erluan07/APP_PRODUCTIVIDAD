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

    // ---- Alarmas: las configuras en la app (pestaña Dormir). Esto solo fija las reglas de edición. ----
    /** Una alarma activa que suena en menos de estas horas no se puede cambiar, apagar ni borrar. */
    const val ALARM_LOCK_HOURS = 3L

    // ---- Modo Dormir: lo activas tú con "Me voy a dormir"; dura hasta tu próxima alarma (tope SLEEP_MAX_HOURS) ----
    const val SLEEP_MAX_HOURS = 14L
    val SLEEP_WHITELIST: Set<String> = setOf(
        APP_PACKAGE,
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.android.incallui", "com.android.server.telecom", "com.android.phone",
        "com.spotify.music", // sonidos para dormir
    )
    val STRICT_PREFIXES: List<String> = listOf("com.spotify")

    /** Texto que debe contener el QR/Barcode (o la etiqueta NFC de texto) pegado en el baño/cocina. */
    const val ALARM_QR_VALUE = "KRONOS-BANO-01"

    /** UIDs NFC permitidos en hexadecimal mayúsculas (opcional; también vale una etiqueta NDEF de texto = ALARM_QR_VALUE). */
    val NFC_UIDS: Set<String> = emptySet()

    /** Usa el micrófono (solo volumen, sin grabar) durante el seguimiento de sueño que inicias con "Me voy a dormir". */
    const val SLEEP_USE_MICROPHONE = true

    /** Smart Wake-Up: delta de aceleración (m/s²) que cuenta como micro-movimiento y nº por época de 30 s. */
    const val SMART_MOVE_DELTA = 0.15f
    const val SMART_MOVE_COUNT = 4

    // ---- Anti-recaída tras despertar ----
    /** Minutos que se vigila después de confirmar "estoy despierto". */
    const val AWAKE_WATCH_MIN = 60L
    /** Minutos sin usar ni mover el teléfono para considerar que te volviste a dormir y volver a sonar. */
    const val AWAKE_IDLE_MIN = 10L
    const val AWAKE_MOVE_DELTA = 0.6f
    const val AWAKE_MOVE_COUNT = 8

    // ---- Device Owner (Fase 5, opcional). Ver docs/DEVICE_OWNER.md ----
    /** Oculta la barra de estado/ajustes rápidos durante el búnker (solo si la app es Device Owner). */
    const val DEVICE_OWNER_DISABLE_STATUS_BAR = true
    /** Impide arrancar en modo seguro durante el búnker. OJO: el modo seguro es tu salida de emergencia. */
    const val DEVICE_OWNER_BLOCK_SAFE_BOOT = false

    // ---- Agenda / Compromiso Diario (Fase 3) ----
    const val AGENDA_MIN_BLOCKS = 1
    const val MIN_BLOCK_MIN = 15L
    /** Tiempo para completar el ritual tras escanear; si se agota, vuelve la alarma completa. */
    const val RITUAL_TIMEOUT_MIN = 10L

    /** Silencio temporal al pulsar "Ya desperté, voy a escanear". Si no escaneas a tiempo, vuelve a sonar. */
    const val MUTE_SECONDS = 25
    /** Veces que se puede pedir el silencio por alarma (Int.MAX_VALUE = sin límite). */
    const val MUTE_MAX_USES = Int.MAX_VALUE

    // ---- Parámetros de comportamiento ----
    const val WAKE_CHECK_MIN_DELAY_MIN = 7
    const val WAKE_CHECK_MAX_DELAY_MIN = 10
    const val WAKE_CHECK_COUNTDOWN_SEC = 90
    const val SMART_WAKE_WINDOW_MIN = 25
    const val BOOT_TRAP_PENALTY_MIN = 60L

    /** Nombres de paquete que jamás deben bloquearse (llamadas de emergencia). */
    fun isAllowed(pkg: String, launchers: Set<String>, imes: Set<String>, strict: Boolean = false): Boolean =
        pkg !in ALWAYS_BLOCKED_IN_BUNKER &&
            BLOCKED_PREFIXES.none { pkg.startsWith(it) } && !pkg.contains(".settings") &&
            (pkg in (if (strict) SLEEP_WHITELIST else WHITELIST) ||
                (if (strict) STRICT_PREFIXES else ALLOWED_PREFIXES).any { pkg.startsWith(it) } ||
                pkg in SYSTEM_ALLOWED || pkg in launchers || pkg in imes)
}
