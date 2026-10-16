# Device Owner (opcional, avanzado)

Con Device Owner la app, **mientras el Modo Búnker está activo**, puede:
- impedir su propia desinstalación,
- ocultar la barra de estado y los ajustes rápidos (`DEVICE_OWNER_DISABLE_STATUS_BAR`),
- opcionalmente impedir el modo seguro (`DEVICE_OWNER_BLOCK_SAFE_BOOT`, **apagado por defecto**).

Fuera del búnker todo se levanta solo. Si la app no es Device Owner, nada de esto se aplica y el resto funciona igual.

## ⚠️ Léelo antes
- **Úsalo en un teléfono secundario primero.** Para activarlo el teléfono debe estar recién restablecido o **sin ninguna cuenta** (Google, Samsung, Xiaomi…) y sin perfiles extra.
- Una app Device Owner **no se puede desinstalar** normalmente, ni siquiera fuera del búnker.
  - En la versión de prueba (debug) hay un botón **"[Prueba] Renunciar a Device Owner"** en la pantalla principal. Úsalo antes de desinstalar.
  - Si algún día generas una versión sin ese botón y pierdes el acceso, la única salida es el **restablecimiento de fábrica**.
- `DEVICE_OWNER_BLOCK_SAFE_BOOT = true` quita también tu salida de emergencia por modo seguro. No lo actives sin pensarlo.
- Las llamadas de emergencia siguen funcionando (el Teléfono está siempre en la lista blanca).

## Activarlo (desde el PC, con depuración USB)
```bash
adb install kronos-focus-debug.apk
adb shell dpm set-device-owner com.kronos.focus/.admin.KronosDeviceAdminReceiver
adb shell pm grant com.kronos.focus android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services com.kronos.focus/com.kronos.focus.services.BunkerAccessibilityService
adb shell settings put secure accessibility_enabled 1
```
Si `set-device-owner` dice "Not allowed to set the device owner because there are already some accounts", quita todas las cuentas del teléfono y repite.

## Comprobar
En la pantalla principal debe decir **"Device Owner: ACTIVO"**. Inicia el búnker de prueba y verifica que Ajustes → Apps no permite desinstalar Kronos.

## Quitarlo
Pantalla principal → **[Prueba] Renunciar a Device Owner** (solo versión debug). Después ya puedes desinstalar con normalidad.

## Boot Trap
Si el teléfono se reinicia con un bloque de búnker o una alarma pendiente, Kronos restaura el bloqueo al arrancar. En versiones **release** suma además **+60 min** de búnker (una sola vez por arranque). En la versión debug no hay penalización.
