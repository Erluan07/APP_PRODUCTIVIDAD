package com.kronos.focus.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kronos.focus.services.TestMode

/** Botón "Salir del Modo prueba" de la notificación: funciona aunque la pantalla esté tapada por la alarma. */
class TestExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = TestMode.exitAll(context)
}
