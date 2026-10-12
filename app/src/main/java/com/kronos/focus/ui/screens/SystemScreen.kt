package com.kronos.focus.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.BuildConfig
import com.kronos.focus.admin.DeviceOwnerHardening
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.services.AlarmFlow
import com.kronos.focus.services.AlarmScheduler
import com.kronos.focus.ui.AppActions
import com.kronos.focus.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun CheckRow(title: String, ok: Boolean, detail: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            MutedText(detail)
        }
        if (ok) StatusPill("OK", Tone.OK)
        else if (actionLabel != null) SoftButton(actionLabel, onAction)
        else StatusPill("Pendiente", Tone.WARN)
    }
}

@Composable
fun SystemScreen(ctx: Context, now: Long, a11y: Boolean, actions: AppActions) {
    val store = BunkerStateStore.get(ctx)
    val camOk = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val fsOk = Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val batOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val heal = AccessibilityGuard.canSelfHeal(ctx)
    val owner = DeviceOwnerHardening.isOwner(ctx)
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    ScreenScaffold("Sistema", "Permisos y protecciones")  {
        AppCard {
            SectionTitle("Permisos", "Kronos necesita esto para bloquear, despertarte y tomar fotos.")
            CheckRow("Servicio de bloqueo", a11y, if (a11y) "Activo" else "Apagado", "Activar") {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            CheckRow("Cámara", camOk, "Para escanear el QR y las fotos de evidencia", "Permitir") { actions.requestCamera() }
            CheckRow("Alarma en pantalla completa", fsOk, "Para que la alarma aparezca con la pantalla bloqueada", "Permitir") {
                if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
            }
            CheckRow("Ahorro de batería", batOk, "Excluye a Kronos para que no lo cierre el sistema", "Excluir") {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
            }
            CheckRow(
                "Auto-reactivación", heal,
                if (heal) "Si apagan el servicio, Kronos lo reactiva" else "Desde el PC: adb shell pm grant com.kronos.focus android.permission.WRITE_SECURE_SETTINGS",
            )
            CheckRow("Device Owner (opcional)", owner, if (owner) "App no desinstalable durante el búnker" else "Avanzado: ver docs/DEVICE_OWNER.md")
        }

        AppCard {
            SectionTitle("Protecciones")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("${store.blockedCount()} bloqueos", Tone.INFO)
                StatusPill("${store.tamperCount()} desactivaciones", if (store.tamperCount() == 0) Tone.OK else Tone.WARN)
            }
            MutedText("Las reglas y la lista blanca están en el código; no hay ajustes en la app para relajarlas.")
        }

        AppCard {
            SectionTitle("Diagnóstico", "Últimas apps vistas durante el búnker (✓ permitida, ✗ bloqueada).")
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val recent = store.recent().take(8)
            if (recent.isEmpty()) MutedText("Sin actividad todavía.")
            recent.forEach { (t, ok, pkg) -> MutedText("${fmt.format(Date(t))}  ${if (ok) "✓" else "✗"}  $pkg") }
        }

        if (BuildConfig.DEBUG) {
            AppCard {
                SectionTitle("Herramientas de prueba", "Solo en la versión de prueba (debug).")
                SoftButton("Hacer sonar la alarma ahora", { AlarmFlow.ring(ctx, test = true) }, Modifier.fillMaxWidth())
                SoftButton("Sonar en 1 minuto (pantalla apagada)", { AlarmScheduler.scheduleTest(ctx, System.currentTimeMillis() + 60_000L) }, Modifier.fillMaxWidth())
                SoftButton("Wake-Up Check ahora", { AlarmFlow.startCheck(ctx) }, Modifier.fillMaxWidth())
                SoftButton("Borrar agenda de hoy", { AgendaStore.get(ctx).clear() }, Modifier.fillMaxWidth())
                if (owner) SoftButton("Renunciar a Device Owner", { DeviceOwnerHardening.release(ctx) }, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
