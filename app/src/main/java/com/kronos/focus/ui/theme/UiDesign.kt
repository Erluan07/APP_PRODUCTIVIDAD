package com.kronos.focus.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Apariencia de la app. Es solo estética (no afecta a ninguna regla), por eso se puede cambiar cuando quieras. */
object UiDesign {
    const val CLASSIC = 0   // teal + naranja, claro/oscuro según el sistema, barra inferior
    const val MIDNIGHT = 1  // oscuro con lima, anillo de enfoque y dock flotante

    var current by mutableIntStateOf(MIDNIGHT)
        private set

    private const val PREFS = "ui_prefs"
    private const val KEY = "design"

    fun load(ctx: Context) {
        current = MIDNIGHT   // diseño definitivo: Medianoche (ya no hay selector)
    }

    fun set(ctx: Context, design: Int) {
        current = design
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, design).apply() }
    }
}
