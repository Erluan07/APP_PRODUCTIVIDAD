package com.kronos.focus.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

    override fun onServiceConnected() {
        state = BunkerStateStore.get(this)
        alarm = com.kronos.focus.data.AlarmStateStore.get(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshInfrastructurePackages()
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
        if (!state.isActive()) { hideOverlay(); lastPackage = pkg; return }

        if (AppRulesConfig.isAllowed(pkg, launchers, imes, strict = state.isSleepMode())) {
            if (pkg != lastPackage) state.recordSeen(pkg, true)
            hideOverlay()
        } else {
            // Los bloqueados se re-bloquean siempre (sin deduplicar) por si el HOME falló.
            state.recordSeen(pkg, false)
            block(pkg)
        }
        lastPackage = pkg
    }

    private fun block(@Suppress("UNUSED_PARAMETER") blockedPackage: String) {
        showOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun showOverlay() {
        if (overlay != null) return
        val sleeping = ::state.isInitialized && state.isSleepMode()
        val title = if (sleeping) "Modo dormir" else "Modo búnker"
        val body = if (sleeping) "\nDescansa: esta app está bloqueada hasta tu alarma." else "\nEnfócate: esta app está bloqueada."
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

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        if (::state.isInitialized && state.isActive()) { state.recordTamper(); AccessibilityGuard.tryEnable(this) }
        return super.onUnbind(intent)
    }
}
