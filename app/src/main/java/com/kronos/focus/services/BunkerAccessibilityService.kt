package com.kronos.focus.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.kronos.focus.data.UsageLimitStore
import com.kronos.focus.domain.limits.LimitBlock
import com.kronos.focus.domain.limits.UsageLimits
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.rules.AppRulesConfig

/**
 * Bloqueo reactivo. Diseño de bajo consumo:
 *  - Solo recibe TYPE_WINDOW_STATE_CHANGED (configurado en XML), sin leer contenido de ventanas.
 *  - Early-exit si el búnker no está activo (una lectura de un Long volátil).
 *  - Launchers/IMEs se cachean y se refrescan solo cuando cambian los paquetes.
 *  - Sin polling ni timers: todo es dirigido por eventos.
 */
class BunkerAccessibilityService : AccessibilityService() {

    private lateinit var state: BunkerStateStore
    private lateinit var alarm: com.kronos.focus.data.AlarmStateStore
    private lateinit var windowManager: WindowManager
    private var overlay: TextView? = null

    @Volatile private var launchers: Set<String> = emptySet()
    @Volatile private var imes: Set<String> = emptySet()
    private var lastPackage: String? = null
    /** true mientras la última app "real" fue una app habilitada: permite sus pantallas auxiliares (adjuntar, cámara). */
    private var fromAllowedApp = false
    private lateinit var allowedApps: com.kronos.focus.data.AllowedAppsStore

    // ---- Límites de uso por app ----
    private lateinit var limits: UsageLimitStore
    private val handler = Handler(Looper.getMainLooper())
    private var trackedPkg: String? = null
    private var trackedSince = 0L                       // 0 = en pausa (pantalla apagada/bloqueada)
    private val contMs = HashMap<String, Long>()        // uso continuo por app
    private val lastLeft = HashMap<String, Long>()      // cuándo salió de cada app (para el reinicio del uso continuo)
    private val tick = Runnable { onUsageTick() }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                accountUsage(now); trackedPkg?.let { lastLeft[it] = now }; trackedSince = 0L; handler.removeCallbacks(tick)
            } else if (intent.action == Intent.ACTION_USER_PRESENT) {
                val p = trackedPkg
                if (p != null && limits.isLimited(p)) { resumeTracking(p, now); startTicking() }
            }
        }
    }

    override fun onServiceConnected() {
        state = BunkerStateStore.get(this)
        alarm = com.kronos.focus.data.AlarmStateStore.get(this)
        allowedApps = com.kronos.focus.data.AllowedAppsStore.get(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        limits = UsageLimitStore.get(this)
        runCatching {
            ContextCompat.registerReceiver(
                this, screenReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        refreshInfrastructurePackages()
    }

    private fun labelOf(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Suma al uso del día el tiempo transcurrido en la app vigilada (como mucho 30 s por tramo, por si faltaron ticks). */
    private fun accountUsage(now: Long) {
        val p = trackedPkg ?: return
        if (trackedSince == 0L) return
        if (limits.isLimited(p)) {
            val d = (now - trackedSince).coerceIn(0L, 30_000L)
            limits.addUsage(p, d, now)
            contMs[p] = (contMs[p] ?: 0L) + d
        }
        trackedSince = now
    }

    private fun resumeTracking(p: String, now: Long) {
        if (now - (lastLeft[p] ?: 0L) > com.kronos.focus.domain.rules.AppRulesConfig.USAGE_CONTINUOUS_RESET_GAP_SEC * 1000L) contMs[p] = 0L
        trackedSince = now
    }

    private fun startTicking() { handler.removeCallbacks(tick); handler.postDelayed(tick, 10_000L) }

    private fun stopTracking() {
        handler.removeCallbacks(tick)
        trackedSince = 0L
        limits.persistUsage()
    }

    private fun onUsageTick() {
        val p = trackedPkg ?: return
        val now = System.currentTimeMillis()
        if (!limits.isLimited(p) || trackedSince == 0L) return
        accountUsage(now)
        if (UsageLimits.continuousExceeded(contMs[p] ?: 0L)) {
            val until = now + com.kronos.focus.domain.rules.AppRulesConfig.USAGE_COOLDOWN_MIN * 60_000L
            limits.startCooldown(p, until)
            contMs[p] = 0L
            stopTracking()
            limitBlock(p, LimitBlock.Cooldown(until))
            return
        }
        val b = limits.blockOf(p, now)
        if (b != null) { stopTracking(); limitBlock(p, b); return }
        handler.postDelayed(tick, 10_000L)
    }

    /** Devuelve true si la app está bloqueada por su límite (ya se la sacó de pantalla). */
    private fun handleLimits(pkg: String): Boolean {
        if (!::limits.isInitialized) return false
        if (!limits.hasAny()) { if (trackedPkg != null) { stopTracking(); trackedPkg = null }; return false }
        // La barra de estado, el teclado y los diálogos del sistema no cuentan como salir de la app.
        if (pkg in AppRulesConfig.SYSTEM_ALLOWED || pkg in imes || pkg == "android") return false
        val now = System.currentTimeMillis()
        if (pkg != trackedPkg) {
            accountUsage(now)
            trackedPkg?.let { lastLeft[it] = now }
            trackedPkg = pkg
            trackedSince = 0L
            if (limits.isLimited(pkg)) resumeTracking(pkg, now) else handler.removeCallbacks(tick)
        }
        if (!limits.isLimited(pkg)) return false
        val b = limits.blockOf(pkg, now)
        if (b != null) { stopTracking(); limitBlock(pkg, b); return true }
        if (trackedSince == 0L) trackedSince = now
        startTicking()
        return false
    }

    private fun limitBlock(pkg: String, b: LimitBlock) {
        val name = labelOf(pkg)
        val body = when (b) {
            is LimitBlock.Cooldown -> "Llevas ${AppRulesConfig.USAGE_CONTINUOUS_MAX_MIN} min seguidos en $name.\nBloqueada hasta las " +
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(b.untilMs)) + "."
            LimitBlock.DailyExhausted -> "Llegaste al máximo de hoy en $name.\nVuelve mañana."
        }
        state.recordSeen(pkg, false)
        showOverlay("Límite de uso", "\n$body")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Mientras suena la alarma (o hay Wake-Up Check) nada puede estar delante de la pantalla de alarma.
        if (alarm.uiRequired()) {
            if (pkg != AppRulesConfig.APP_PACKAGE && pkg !in AppRulesConfig.SYSTEM_ALLOWED && pkg !in imes) {
                runCatching {
                    startActivity(Intent(this, com.kronos.focus.ui.alarm.AlarmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
            }
            lastPackage = pkg
            return
        }
        if (handleLimits(pkg)) { lastPackage = pkg; return }
        if (!state.isActive()) { hideOverlay(); lastPackage = pkg; return }

        val strict = state.isSleepMode()
        val userApps = allowedApps.chosen()
        val isUserApp = !strict && AppRulesConfig.isUserApp(pkg, userApps)
        if (isUserApp) fromAllowedApp = true
        val helper = !strict && fromAllowedApp && pkg in AppRulesConfig.APP_HELPERS
        if (helper || AppRulesConfig.isAllowed(pkg, launchers, imes, strict = strict, userApps = userApps)) {
            if (!helper && !isUserApp && pkg !in AppRulesConfig.SYSTEM_ALLOWED && pkg !in imes) fromAllowedApp = false
            if (pkg != lastPackage) state.recordSeen(pkg, true)
            hideOverlay()
        } else {
            // Los bloqueados se re-bloquean siempre (sin deduplicar) por si el HOME falló.
            state.recordSeen(pkg, false)
            fromAllowedApp = false
            block(pkg)
        }
        lastPackage = pkg
    }

    private fun block(@Suppress("UNUSED_PARAMETER") blockedPackage: String) {
        showOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun showOverlay(customTitle: String? = null, customBody: String? = null) {
        if (customTitle != null) hideOverlay() else if (overlay != null) return
        val sleeping = ::state.isInitialized && state.isSleepMode()
        val title = customTitle ?: if (sleeping) "Modo dormir" else "Modo búnker"
        val onBreak = !sleeping && ::state.isInitialized && state.pomodoroPhase()?.focus == false
        val body = customBody ?: if (onBreak) "\nDescanso: estira, camina, toma agua. Las apps siguen bloqueadas." else if (sleeping) "\nDescansa: en Modo dormir solo funcionan Teléfono y Spotify (WhatsApp también queda bloqueado) hasta tu alarma." else "\nEnfócate: esta app está bloqueada."
        val view = TextView(this).apply {
            text = android.text.SpannableStringBuilder(title + body).apply {
                setSpan(android.text.style.RelativeSizeSpan(1.7f), 0, title.length, 0)
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, title.length, 0)
            }
            textSize = 18f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
            setPadding(72, 72, 72, 72)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#F5042F2E"))
            runCatching { typeface = android.graphics.Typeface.create(resources.getFont(com.kronos.focus.R.font.plus_jakarta_sans), android.graphics.Typeface.NORMAL) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // No requiere SYSTEM_ALERT_WINDOW cuando lo crea un AccessibilityService.
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(view, params)
        overlay = view
        // El overlay se retira solo al volver a una app permitida (ver onAccessibilityEvent).
    }

    private fun hideOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun refreshInfrastructurePackages() {
        val pm = packageManager
        launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
        imes = (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .inputMethodList.map { it.packageName }.toSet()
    }

    override fun onInterrupt() = hideOverlay()

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        if (::state.isInitialized && state.isActive()) { state.recordTamper(); AccessibilityGuard.tryEnable(this) }
        return super.onUnbind(intent)
    }
}
