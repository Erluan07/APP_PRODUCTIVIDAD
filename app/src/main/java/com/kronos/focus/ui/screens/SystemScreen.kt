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
import androidx.compose.runtime.remember
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
fun SystemSection(ctx: Context, now: Long, a11y: Boolean, actions: AppActions) {
    val store = BunkerStateStore.get(ctx)
    val camOk = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val fsOk = Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val batOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val heal = AccessibilityGuard.canSelfHeal(ctx)
    val owner = DeviceOwnerHardening.isOwner(ctx)
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    ScreenScaffold {
        AppCard {
            SectionTitle("Permisos", "Kronos necesita esto para bloquear, despertarte y tomar fotos.")
            CheckRow("Servicio de bloqueo", a11y, if (a11y) "Activo" else "Apagado", "Activar") {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            CheckRow(
                "Sensor de pasos", ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
                "Para comprobar que caminas después de despertar (sin él es menos fiable)", "Permitir",
            ) { actions.requestSteps() }
            CheckRow("Cámara", camOk, "Para escanear el QR y las fotos de evidencia", "Permitir") { actions.requestCamera() }
            CheckRow("Calendario", com.kronos.focus.data.CalendarReader.hasPermission(ctx), "Para ver tus reuniones junto a tus tareas", "Permitir") { actions.requestCalendar() }
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

        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            AppCard {
                SectionTitle("Samsung: que no cierre Kronos", "Samsung suspende apps en segundo plano y puede apagar la alarma.")
                MutedText("Ajustes > Batería > Límites de uso en segundo plano: quita Kronos Focus de “Apps en suspensión” y “Apps en suspensión profunda”, y añádela a “Apps que nunca se suspenden”.")
            }
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
            SectionTitle("Verificación con IA", "Un modelo de visión que corre en tu teléfono, sin internet, para comprobar la foto de las tareas obligatorias.")
            val ready = com.kronos.focus.vision.VisionModel.isReady(ctx)
            val pct = com.kronos.focus.vision.VisionModel.progressPercent
            MutedText(if (ready) "Modelo cargado (${com.kronos.focus.vision.VisionModel.sizeMb(ctx)} MB)." else "Sin modelo. Carga el archivo .litertlm que descargaste (unos 5 GB; necesitas espacio libre para la copia).")
            if (pct != null) {
                androidx.compose.material3.LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                MutedText("Copiando… $pct %. No cierres la app.")
            } else {
                SoftButton(if (ready) "Cargar otro modelo" else "Cargar modelo (.litertlm)", { actions.importModel() }, Modifier.fillMaxWidth())
            }
            com.kronos.focus.vision.VisionModel.lastMessage?.let { MutedText(it) }
        }

        AppCard {
            val chosen = com.kronos.focus.data.AllowedAppsStore.get(ctx).chosen().orEmpty()
            SectionTitle("Apps habilitadas en el búnker", "Elegidas la primera vez; ya no se pueden cambiar. El Teléfono siempre está permitido.")
            val pm = ctx.packageManager
            val names = remember(chosen) {
                chosen.map { p -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p) }.sorted()
            }
            MutedText(if (names.isEmpty()) "Ninguna (solo el Teléfono)." else names.joinToString(" · "))
        }

        Spacer(Modifier.height(4.dp))
    }
}
