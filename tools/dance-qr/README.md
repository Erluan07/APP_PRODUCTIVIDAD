# QR bailable (programa del PC)

Para que la alarma del teléfono solo se apague **después de bailar**: la página abre tu música en Spotify, enciende la
cámara del PC, comprueba que llevas 30 s moviéndote y entonces genera un QR **nuevo** que apaga la alarma.

## Uso (una vez)
1. En el teléfono: **Más → Sistema → QR bailable → Copiar clave**.
2. En el PC: abre `index.html` (doble clic, con Chrome o Edge), pega la clave y, si quieres, un enlace de Spotify
   (`spotify:playlist:…` o `https://open.spotify.com/…`); vacío abre la aplicación de Spotify. Se recuerda en ese navegador.
   La primera vez Chrome/Edge preguntan "¿Abrir Spotify?": acepta (y marca "permitir siempre").

## Ponerlo en la barra de tareas (Windows)
1. Deja todos los archivos de esta carpeta juntos (`index.html`, `dance-core.js`, `qrcode.js`, `icono.ico`,
   `icono.png`, `crear-acceso-directo.bat` y `crear-acceso-directo.ps1`) en un sitio fijo, por ejemplo
   `C:\Kronos\dance-qr`. Si la mueves después, vuelve a crear el acceso directo.
2. Doble clic en **`crear-acceso-directo.bat`**. Crea **"QR bailable Kronos"** en el Escritorio, con el ícono de
   `icono.ico`, y abre la página en su propia ventana (sin pestañas).
3. En el Escritorio: clic derecho sobre "QR bailable Kronos" → (*Mostrar más opciones* en Windows 11) →
   **Anclar a la barra de tareas**.
4. **Tu logo:** cambia `icono.ico` por el tuyo (un `.ico`; si lo tienes en PNG, conviértelo en
   convertio.co/es/png-ico) y vuelve a hacer doble clic en `crear-acceso-directo.bat`. Para el ícono de la
   pestaña de la página, cambia también `icono.png`.

Sin el script: en Chrome/Edge, con la página abierta, menú ⋮ → *Guardar y compartir* → *Crear acceso directo…*
→ marca *Abrir como ventana*. Ese acceso directo también se ancla con clic derecho.

Nota: como la página se abre desde un archivo, el navegador te pide permiso de cámara cada vez; acepta
"Permitir esta vez".

## Cada noche
Al pulsar **Me voy a dormir** en el teléfono, elige **QR bailable ✨**. Por la mañana, con la alarma sonando:
pulsa **Empezar a bailar** en el PC, baila 30 s y escanea el QR con la pantalla de alarma.

## Cómo evita las capturas de pantalla
El QR lleva el minuto actual firmado con la clave (HMAC-SHA256). Vale unos 3 minutos y solo si se generó **después**
de que empezó a sonar la alarma: una captura de ayer no sirve y tampoco se puede generar de antemano. El QR normal
(el del baño) no vale en modo bailable. Para que cuadre, **el teléfono y el PC deben tener la hora automática**.

## Límites (sé honesto contigo)
- El baile se detecta por **movimiento de la cámara** (hace falta mover varias zonas del cuerpo; una sola mano no
  basta). No sabe si de verdad suena música ni distingue un baile de saltar sin ganas.
- La clave vive en tu PC: quien tenga la clave puede generar el código sin bailar. Es una ayuda para no dormirte,
  no una cerradura.
- Si eliges bailable y el PC no está contigo, no podrás apagar la alarma.

## Pruebas
`node tools/dance-qr/test.mjs` (usa el mismo vector de prueba que `RulesTest.kt`).
