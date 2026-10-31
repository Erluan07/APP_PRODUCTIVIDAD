package com.kronos.focus.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.kronos.focus.domain.rules.AppRulesConfig

/**
 * Endurecimiento opcional cuando la app es Device Owner (se configura por ADB, ver docs/DEVICE_OWNER.md).
 * Solo aplica restricciones MIENTRAS el Modo Búnker está activo y las levanta al terminar.
 * Si la app no es Device Owner no hace nada.
 */
object DeviceOwnerHardening {
    @Volatile private var lastApplied: Boolean? = null

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, KronosDeviceAdminReceiver::class.java)

    fun isOwner(ctx: Context): Boolean = runCatching { dpm(ctx).isDeviceOwnerApp(ctx.packageName) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun sync(ctx: Context, bunkerActive: Boolean) {
        if (lastApplied == bunkerActive || !isOwner(ctx)) return
        val d = dpm(ctx)
        val a = admin(ctx)
        runCatching { d.setUninstallBlocked(a, ctx.packageName, bunkerActive) }
        if (AppRulesConfig.DEVICE_OWNER_DISABLE_STATUS_BAR) runCatching { d.setStatusBarDisabled(a, bunkerActive) }
        if (AppRulesConfig.DEVICE_OWNER_BLOCK_SAFE_BOOT) runCatching {
            if (bunkerActive) d.addUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT)
            else d.clearUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT)
        }
        lastApplied = bunkerActive
    }
}
