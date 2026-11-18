package com.kronos.focus.services

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Comprueba/reactiva el servicio de accesibilidad. Reactivar requiere WRITE_SECURE_SETTINGS (por ADB). */
object AccessibilityGuard {
    private fun me(ctx: Context) = ComponentName(ctx, BunkerAccessibilityService::class.java).flattenToString()

    fun isEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(me(ctx), ignoreCase = true) }
    }

    fun canSelfHeal(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun tryEnable(ctx: Context): Boolean {
        if (isEnabled(ctx)) return true
        if (!canSelfHeal(ctx)) return false
        return runCatching {
            val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val new = if (cur.isEmpty()) me(ctx) else "$cur:${me(ctx)}"
            Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, new)
            Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }
}
