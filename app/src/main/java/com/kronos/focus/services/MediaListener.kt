package com.kronos.focus.services

import android.service.notification.NotificationListenerService

/**
 * No lee ni guarda notificaciones. Existe solo para que Android permita a Kronos controlar la reproducción
 * (MediaSessionManager.getActiveSessions exige un listener de notificaciones activado).
 */
class MediaListener : NotificationListenerService()
