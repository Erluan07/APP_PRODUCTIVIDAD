# Kronos Focus — Plan Maestro de Implementación

App Android nativa, 100 % local, $0, reglas inmutables en código. Este documento es la guía de ingeniería; los archivos de la Fase 1 ya están en el repo.

> **Estado:** Fase 1 en esqueleto (manifest, `AppRulesConfig`, `BunkerAccessibilityService`, stubs). No compilado todavía (no hay Android SDK en el entorno de nube); la primera tarea local es abrirlo en Android Studio.

---

## 1. Stack tecnológico

| Área | Elección | Motivo |
|---|---|---|
| Lenguaje / UI | Kotlin 2.0.x + Jetpack Compose (Material 3) | Requisito |
| minSdk / targetSdk | **29 (Android 10) / 35 (Android 15)** | Q+ cubre `TYPE_ACCESSIBILITY_OVERLAY`, Direct Boot estable, scoped storage. Sideload: sin restricciones de Play Store sobre `USE_EXACT_ALARM` ni Accessibility |
| Escaneo QR/Barcode | ML Kit `barcode-scanning` (modelo **empaquetado**) + CameraX | Offline, sin Play Services en runtime |
| NFC | `NfcAdapter` + reader mode / foreground dispatch | Nativo, sin dependencias |
| Cámara evidencia | CameraX (`ImageCapture`) | Captura en vivo, sin picker de galería |
| Persistencia | Room (evidencias, bloques, log) + SharedPreferences *device-protected* (estado crítico en Direct Boot) | Room vive en almacenamiento protegido por credenciales; el estado del búnker debe leerse antes del primer desbloqueo |
| Sensores | `SensorManager` (acelerómetro, `TYPE_ACCELEROMETER`) | Smart Wake-Up |
| Alarmas | `AlarmManager.setAlarmClock()` | Exacta, exenta de Doze, visible como alarma del sistema |
| Sistema profundo | `AccessibilityService`, Device Owner (`DevicePolicyManager`) | Bloqueo e indestructibilidad |

## 2. Arquitectura y permisos

### 2.1 Permisos (ver `AndroidManifest.xml`)
`SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE` (en el `<service>`), `WRITE_SECURE_SETTINGS` (vía ADB), `FOREGROUND_SERVICE` + `_SPECIAL_USE` + `_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `MODIFY_AUDIO_SETTINGS`, `NFC`, `VIBRATE`, `HIGH_SAMPLING_RATE_SENSORS`, `ACTIVITY_RECOGNITION`, `CAMERA`, `RECEIVE_BOOT_COMPLETED` (+ `LOCKED_BOOT_COMPLETED` vía intent-filter), `REQUEST_DELETE_PACKAGES`.

Notas Android 13+/14+: los servicios de accesibilidad instalados fuera de la tienda están en "ajustes restringidos"; se resuelve concediendo `WRITE_SECURE_SETTINGS` por ADB y activando el servicio desde ADB (comandos abajo), sin tocar la UI de Ajustes.

### 2.2 AccessibilityService reactivo y barato
Implementado en `services/BunkerAccessibilityService.kt`:
1. XML: solo `typeWindowStateChanged`, `canRetrieveWindowContent=false`, `notificationTimeout=0` → sin lectura de contenido, ~0 CPU en reposo.
2. Primera línea del handler: `state.isActive()` (un `Long` volátil). Fuera de búnker no hace nada.
3. Deduplicación por último paquete; launchers e IMEs cacheados (se refrescan en `onServiceConnected`; Fase 1 añade receiver de `PACKAGE_ADDED/REMOVED`).
4. Acción de bloqueo: overlay `TYPE_ACCESSIBILITY_OVERLAY` + `GLOBAL_ACTION_HOME`. Sin polling ni timers.
5. Fase 1 (pendiente): sustituir `TextView` por `ComposeView` con cuenta regresiva del bloque; vigilar `com.android.settings` y diálogos de desinstalación (ya en `ALWAYS_BLOCKED_IN_BUNKER`).
6. Fase 1 (pendiente): foreground service ligero que mantenga viva la app y notificación persistente.

### 2.3 Device Owner por ADB
Requisitos: dispositivo recién reseteado o **sin ninguna cuenta** (Google/Samsung) ni usuarios secundarios.
```bash
adb install app-debug.apk
adb shell dpm set-device-owner com.kronos.focus/.admin.KronosDeviceAdminReceiver
adb shell pm grant com.kronos.focus android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services \
  com.kronos.focus/com.kronos.focus.services.BunkerAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.kronos.focus SYSTEM_ALERT_WINDOW allow
```
Con Device Owner (Fase 5): `setUninstallBlocked`, `setStatusBarDisabled`, `addUserRestriction(DISALLOW_SAFE_BOOT / DISALLOW_FACTORY_RESET / DISALLOW_APPS_CONTROL / DISALLOW_DEBUGGING_FEATURES opcional)`, `setLockTaskPackages` + lock task mode durante el búnker, `setKeyguardDisabledFeatures`.

> **Válvula de escape deliberada y única:** con depuración USB activa, `adb shell dpm remove-active-admin com.kronos.focus/.admin.KronosDeviceAdminReceiver` (o recompilar) deshace todo. No dejes `DISALLOW_DEBUGGING_FEATURES` activo en builds de desarrollo; si lo activas en producción, tu única salida es el reset de fábrica. **La whitelist incluye el Teléfono para que las llamadas de emergencia sigan funcionando.**

### 2.4 Alarma implacable (Fase 2)
- `AlarmManager.setAlarmClock()` → `AlarmReceiver` → `AlarmService` (foreground, `mediaPlayback`) → `AlarmMissionActivity` (`showWhenLocked`, `turnScreenOn`, full-screen intent).
- Audio: `USAGE_ALARM`, `setStreamVolume(STREAM_ALARM, max)` y `ContentObserver` sobre `Settings.System` + `onKeyDown` para volver a forzar máximo. `AudioFocus` permanente.
- Persistencia: si la actividad pierde foco (`onPause`/pantalla apagada) el servicio relanza la activity; con Device Owner se usa lock task mode.
- Misión 1: escaneo de QR/Barcode (hash SHA-256 del valor esperado en `AppRulesConfig`) o NFC (UID/NDEF esperado). Silencia 10 min como máximo; luego exige Misión 2.
- Smart Wake-Up: foreground service desde T-25 min, ventana deslizante de varianza del acelerómetro; dispara en el primer pico de movimiento (N1/N2) o a la hora límite. Se desactiva si el teléfono está fuera de la cama (dato ruidoso → fallback a hora límite).
- Wake-Up Check: `setExactAndAllowWhileIdle` a +7–10 min; notificación full-screen con cuenta de 90 s; si expira → reactivar `AlarmService`.

## 3. Estructura del proyecto

```
app/src/main/java/com/kronos/focus/
├── KronosApp.kt
├── domain/
│   ├── rules/AppRulesConfig.kt      # constantes inmutables
│   ├── model/ (TaskBlock, Evidence, AlarmState)
│   └── usecase/ (ScheduleBunkerBlocks, ValidateMission, ApplyBootPenalty)
├── data/
│   ├── BunkerStateStore.kt          # prefs device-protected
│   └── db/ (Room: KronosDatabase, TaskBlockDao, EvidenceDao)
├── services/
│   ├── BunkerAccessibilityService.kt
│   ├── AlarmService.kt
│   └── SmartWakeService.kt
├── receivers/ (BootReceiver, AlarmReceiver, BlockTriggerReceiver, WakeCheckReceiver)
├── admin/KronosDeviceAdminReceiver.kt
└── ui/ (MainActivity, alarm/, agenda/, proof/, bunker/)
```

## 4. Constantes inmutables
`domain/rules/AppRulesConfig.kt`: whitelist de paquetes (Teléfono multi-OEM, WhatsApp/Business, Spotify, la propia app), paquetes de infraestructura permitidos, paquetes siempre bloqueados (Ajustes, instalador) y parámetros (7–10 min, 90 s, 25 min, +60 min). Reglas:
- Solo `const val` / `val` inmutables; ningún acceso a prefs, red ni archivos.
- Ninguna pantalla de ajustes; ningún `Intent` público que cambie reglas (`exported=false` salvo lo mínimo).
- Los códigos QR/NFC esperados se guardan como hash en este mismo archivo (Fase 2).

## 5. Roadmap

| Fase | Entregable | Criterio de aceptación |
|---|---|---|
| **1** | Proyecto base, manifest, whitelist, bloqueo reactivo + overlay, `BunkerStateStore`, foreground service, modo búnker manual de prueba (build debug) | Abrir Instagram/YouTube en búnker → overlay + Home en <200 ms; WhatsApp/Spotify/Teléfono abren |
| **2** | Alarma inapagable, escaneo QR/NFC, Wake-Up Check, Smart Wake-Up | Sin botón descartar; volumen forzado; reapertura tras apagar pantalla |
| **3** | Ritual de agenda (bloques nombre/inicio/fin), validación de solapes, programación automática de bloques | La alarma solo termina con ≥1 bloque válido; los bloques activan el búnker solos |
| **4** | CameraX en vivo + Room + log append-only con hash encadenado | Sin acceso a galería; timestamp de sistema; entradas no editables |
| **5** | Boot Trap (Direct Boot) + Device Owner (hardening) | Reinicio forzado → búnker inmediato +60 min; app no desinstalable |

## 6. Riesgos y decisiones abiertas
1. **Seguridad personal:** sin botón de emergencia; mantener el Teléfono en whitelist y conservar el acceso ADB como única salida.
2. **OEM agresivos (Xiaomi/Samsung/Huawei):** pueden matar servicios; hace falta excluir de optimización de batería (`dpm`/`cmd deviceidle whitelist +com.kronos.focus`).
3. **Smart Wake-Up:** el acelerómetro con el móvil sobre el colchón es una señal débil; tratar como mejora, no garantía.
4. **Boot Trap:** nadie puede impedir que el hardware se reinicie; solo se penaliza y se restaura el bloqueo.
5. **Proof of Work:** sin nube no se puede verificar *qué* muestra la foto; es compromiso conductual, no verificación semántica (opcional: ML Kit offline para etiquetar).
