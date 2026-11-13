package com.kronos.focus.domain.rules

/**
 * REGLAS INMUTABLES.
 *
 * No existe UI ni almacenamiento que modifique estos valores. Cambiarlos exige editar este
 * archivo y recompilar/instalar por USB. No añadir NUNCA lectura desde prefs, red ni archivos.
 */
object AppRulesConfig {

    const val APP_PACKAGE = "com.kronos.focus"

    /** Siempre permitido en el búnker, pase lo que pase: la propia app y el Teléfono (llamadas de emergencia). */
    val CORE_ALLOWED: Set<String> = setOf(
        APP_PACKAGE,
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.android.incallui", "com.android.server.telecom", "com.android.phone",
    )

    /**
     * Apps sugeridas (preseleccionadas) la primera vez que abres Kronos. Ahí eliges cuáles quedan habilitadas
     * en el búnker; al confirmar la elección se bloquea y ya no se puede cambiar (ver AllowedAppsStore).
     */
    val DEFAULT_USER_APPS: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "com.spotify.music",
        "com.erluan.taskmanageria", // Task Manager IA
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.google.android.contacts", "com.samsung.android.app.contacts", "com.android.contacts",
    )

    /** Apps habilitadas por defecto (hasta que se elige la lista). */
    val WHITELIST: Set<String> = CORE_ALLOWED + DEFAULT_USER_APPS

    /** Prefijos permitidos (variantes de la misma app: WhatsApp Business, procesos auxiliares). */
    val ALLOWED_PREFIXES: List<String> = listOf("com.whatsapp", "com.spotify", "com.bancolombia", "com.todo1.mobile")  // Bancolombia (transferencias)

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

    // ---- Pomodoro (opción al iniciar un búnker manual): el bloqueo NO se levanta en el descanso, solo te avisa ----
    const val POMODORO_FOCUS_MIN = 50L
    const val POMODORO_BREAK_MIN = 10L

    // ---- Límites de uso por app (pestaña Más → Límites) ----
    /** Minutos seguidos en una app con límite antes de sacarte de ella. */
    const val USAGE_CONTINUOUS_MAX_MIN = 15L
    /** Tras sacarte por uso continuo, la app queda bloqueada estos minutos. */
    const val USAGE_COOLDOWN_MIN = 10L
    /** Si sales de la app menos de esto, el uso continuo NO se reinicia (evita el truco de salir y volver a entrar). */
    const val USAGE_CONTINUOUS_RESET_GAP_SEC = 120L
    const val USAGE_MIN_LIMIT_MIN = 5
    const val USAGE_MAX_LIMIT_MIN = 480

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

    // ---- Anti-recaída: tras el escaneo hay que SEGUIR CAMINANDO (usar el teléfono en la cama ya no cuenta) ----
    /** Minutos que se vigila DESPUÉS de confirmar "estoy despierto" (la vigilancia empieza antes: desde que escaneas). */
    const val AWAKE_WATCH_MIN = 20L
    /** En cada ventana de este tiempo debes dar al menos [AWAKE_MIN_STEPS] pasos con el teléfono encima. */
    const val AWAKE_WINDOW_MIN = 5L
    const val AWAKE_MIN_STEPS = 20
    /** Si no los diste: aviso y, pasados estos segundos sin caminar, vuelve a sonar la alarma completa. */
    const val AWAKE_GRACE_SEC = 60L
    /** Sin sensor de pasos: movimiento fuerte del teléfono (delta de aceleración y nº por 30 s) equivale a pasos. */
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
    /** Tiempo máximo para terminar la rutina de la mañana; si no, vuelve a sonar la alarma completa. */
    const val ROUTINE_TIMEOUT_MIN = 60L
    /** Durante la rutina la música no se puede pausar y el volumen queda fijo en esta fracción del máximo (1 = máximo). */
    const val ROUTINE_MIN_VOLUME = 1.0f
    const val WAKE_CHECK_MIN_DELAY_MIN = 7
    const val WAKE_CHECK_MAX_DELAY_MIN = 10
    const val WAKE_CHECK_COUNTDOWN_SEC = 90
    const val SMART_WAKE_WINDOW_MIN = 25
    const val BOOT_TRAP_PENALTY_MIN = 60L
    /** Si es true, un reinicio con el búnker activo o una alarma pendiente suma BOOT_TRAP_PENALTY_MIN min de búnker. */
    const val BOOT_PENALTY_ENABLED = false

    /**
     * Pantallas del sistema que una app permitida abre al adjuntar/compartir/hacer foto/elegir contacto.
     * Solo se permiten mientras vienes de una app permitida (ver servicio de bloqueo); abiertas por su
     * cuenta (desde el launcher) siguen bloqueadas. Navegadores y tiendas NO están aquí.
     */
    val APP_HELPERS: Set<String> = setOf(
        "android", // selector/chooser/diálogos del sistema
        "com.google.android.gms", "com.google.android.documentsui", "com.android.documentsui",
        "com.android.providers.media", "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.sec.android.app.camera", "com.samsung.android.camera", "com.android.camera", "com.android.camera2",
        "com.google.android.GoogleCamera", "com.sec.android.gallery3d", "com.samsung.android.gallery3d",
        "com.google.android.apps.photos", "com.samsung.android.app.contacts", "com.android.contacts",
        "com.google.android.contacts", "com.samsung.android.dialer",
        "com.samsung.android.app.smartcapture", "com.sec.android.app.myfiles", "com.samsung.android.provider.filterprovider",
        "com.samsung.android.mdx", "com.samsung.android.scs", "com.samsung.android.bixby.service",
        "com.android.intentresolver",
        // Aviso de huella / biometría (Samsung lo muestra como "Configuración"): lo abre Bancolombia al pedir la huella.
        "com.samsung.android.biometrics.app.setting", "com.samsung.android.authfw", "com.samsung.android.bio.face.service",
    )

    /** Pantallas de huella/rostro de otros fabricantes: mismos permisos que [APP_HELPERS] (solo viniendo de una app permitida). */
    private val HELPER_KEYWORDS = listOf("biometric", "fingerprint", "authfw")

    fun isAppHelper(pkg: String): Boolean =
        pkg in APP_HELPERS || (!pkg.contains(".settings") && HELPER_KEYWORDS.any { pkg.contains(it) })

    /** Nombres de paquete que jamás deben bloquearse (llamadas de emergencia). */
    /** ¿Es una app de usuario habilitada? [userApps] = la lista elegida y bloqueada; null = aún sin elegir (valores por defecto). */
    fun isUserApp(pkg: String, userApps: Set<String>?): Boolean =
        if (userApps != null) pkg in userApps || userApps.any { pkg.startsWith("$it.") }
        else pkg in DEFAULT_USER_APPS || ALLOWED_PREFIXES.any { pkg.startsWith(it) }

    fun isAllowed(pkg: String, launchers: Set<String>, imes: Set<String>, strict: Boolean = false, userApps: Set<String>? = null): Boolean {
        if (pkg in ALWAYS_BLOCKED_IN_BUNKER || BLOCKED_PREFIXES.any { pkg.startsWith(it) } || pkg.contains(".settings")) return false
        val app = if (strict) pkg in SLEEP_WHITELIST || STRICT_PREFIXES.any { pkg.startsWith(it) }
        else pkg in CORE_ALLOWED || isUserApp(pkg, userApps)
        return app || pkg in SYSTEM_ALLOWED || pkg in launchers || pkg in imes
    }
}
