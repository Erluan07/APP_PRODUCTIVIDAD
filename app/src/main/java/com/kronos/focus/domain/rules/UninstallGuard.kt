package com.kronos.focus.domain.rules

/**
 * Detecta la ventana del sistema que pide confirmar la desinstalación de Kronos Focus, venga de donde venga
 * (Ajustes → Apps, mantener pulsado el icono, Play Store…): todas terminan en el mismo diálogo del instalador.
 * Las instalaciones y actualizaciones usan otras pantallas (no contienen "uninstall"), así que siguen funcionando.
 * No toca la desinstalación de otras apps si el diálogo dice claramente cuál es.
 */
object UninstallGuard {
    /** [className]: clase de la ventana (p. ej. com.android.packageinstaller.UninstallerActivity); [text]: texto del diálogo. */
    fun shouldBlock(className: String?, text: String): Boolean {
        val c = (className ?: "").lowercase()
        val t = text.lowercase()
        val mentionsUs = t.contains("kronos")
        // Por la clase: es un diálogo de desinstalación. Si el texto nombra otra app, se deja pasar; si no se sabe cuál, se bloquea.
        if (c.contains("ninstall")) return t.isBlank() || mentionsUs
        // Respaldo por texto, por si el fabricante usa otra clase.
        return mentionsUs && (t.contains("desinstal") || t.contains("uninstall"))
    }
}
