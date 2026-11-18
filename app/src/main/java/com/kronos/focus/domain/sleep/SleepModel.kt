package com.kronos.focus.domain.sleep

import kotlin.math.max
import kotlin.math.min

/** Parámetros personales (se ajustan solos con tu valoración de cada mañana). */
data class SleepParams(val windowMin: Int = 25, val threshold: Float = 0.55f)

/** Resumen de 30 s: nº de micro-movimientos del acelerómetro y si el micrófono detectó un sonido sobre el ruido de fondo. */
data class Epoch(val moves: Int, val soundEvent: Boolean, val micValid: Boolean)

/**
 * Modelo heurístico (sin datos clínicos): un teléfono no mide fases de sueño, así que se combinan
 * (1) movimiento reciente, (2) sonido reciente (opcional) y (3) en qué punto del ciclo de ~90 min estás.
 * Despierta cuando, dentro de la ventana previa a la alarma, la puntuación supera el umbral.
 */
object SleepModel {
    const val CYCLE_MIN = 90.0
    private const val MOVE_NORM = 6.0

    /** 0 = sueño profundo probable ... 1 = sueño ligero/REM probable, según el minuto dentro del ciclo. */
    fun cycleLightness(minutesSinceOnset: Double): Double {
        val phase = (((minutesSinceOnset % CYCLE_MIN) + CYCLE_MIN) % CYCLE_MIN) / CYCLE_MIN
        val base = if (phase < 0.35) 0.1 else 0.1 + 0.9 * ((phase - 0.35) / 0.65)
        val lateNight = (minutesSinceOnset / (8 * 60.0)).coerceIn(0.0, 1.0) * 0.15 // los últimos ciclos son más ligeros
        return min(1.0, base + lateNight)
    }

    /** 0..1 de "agitación" en las épocas recientes. */
    fun activity(recent: List<Epoch>): Double {
        if (recent.isEmpty()) return 0.0
        val stir = (recent.sumOf { it.moves } / MOVE_NORM).coerceIn(0.0, 1.0)
        val mic = recent.filter { it.micValid }
        if (mic.isEmpty()) return stir
        val sound = (mic.count { it.soundEvent } / 3.0).coerceIn(0.0, 1.0)
        return 0.7 * stir + 0.3 * sound
    }

    fun score(recent: List<Epoch>, minutesSinceOnset: Double?): Double {
        val light = minutesSinceOnset?.let { cycleLightness(it) } ?: 0.5
        return 0.65 * activity(recent) + 0.35 * light
    }

    fun shouldWake(recent: List<Epoch>, minutesSinceOnset: Double?, minutesToDeadline: Double, p: SleepParams): Boolean =
        minutesToDeadline in 0.0..p.windowMin.toDouble() && score(recent.takeLast(6), minutesSinceOnset) >= p.threshold

    /**
     * Aprendizaje con tu valoración (1..5) y cuántos minutos antes de la alarma te despertó:
     *  - mal y muy pronto  -> ventana más corta y umbral más exigente (despertar menos antes)
     *  - mal y justo a la hora de la alarma -> ventana más larga y umbral más suave (buscar sueño ligero antes)
     *  - bien -> no se toca
     */
    fun adjust(p: SleepParams, rating: Int, wokeEarlyMin: Int): SleepParams = when {
        rating <= 2 && wokeEarlyMin >= 10 -> p.copy(windowMin = max(5, p.windowMin - 5), threshold = min(0.8f, p.threshold + 0.05f))
        rating <= 2 -> p.copy(windowMin = min(30, p.windowMin + 5), threshold = max(0.4f, p.threshold - 0.05f))
        else -> p
    }
}
