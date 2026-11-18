# QR bailable (programa del PC)

Para que la alarma del teléfono solo se apague **después de bailar**: la página abre tu música en Spotify, enciende la
cámara del PC, comprueba que llevas 30 s moviéndote y entonces genera un QR **nuevo** que apaga la alarma.

## Uso (una vez)
1. En el teléfono: **Más → Sistema → QR bailable → Copiar clave**.
2. En el PC: abre `index.html` (doble clic, con Chrome o Edge), pega la clave y, si quieres, un enlace de Spotify
   (`spotify:playlist:…` o `https://open.spotify.com/…`). Se recuerda en ese navegador.

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
