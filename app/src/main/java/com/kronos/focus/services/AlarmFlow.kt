package com.kronos.focus.services

import android.content.Context
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import kotlin.random.Random

/** Máquina de estados de la alarma. Toda transición pasa por aquí. */
object AlarmFlow {

    /** Hora límite o Smart Wake-Up: empieza a sonar. [test] no consume la alarma real del día. */
    fun ring(ctx: Context, test: Boolean = false) {
        val s = AlarmStateStore.get(ctx)
        if (!test) {
            if (s.lastRingDay == AlarmScheduler.epochDay()) return
            s.lastRingDay = AlarmScheduler.epochDay()
            AlarmScheduler.cancelRing(ctx)
            resetMute(s)
            s.phase = AlarmPhase.RINGING
            AlarmScheduler.scheduleDaily(ctx) // siguiente día
        } else {
            resetMute(s)
            s.phase = AlarmPhase.RINGING
        }
        AlarmService.sync(ctx)
    }

    /** "Ya desperté, voy a escanear": silencia [AppRulesConfig.MUTE_SECONDS] s; si no escanea, vuelve el ruido. */
    fun muteForScan(ctx: Context): Boolean {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.RINGING || s.muteUses >= AppRulesConfig.MUTE_MAX_USES) return false
        s.muteUses += 1
        s.muteUntilMs = System.currentTimeMillis() + AppRulesConfig.MUTE_SECONDS * 1000L
        return true
    }

    private fun resetMute(s: AlarmStateStore) { s.muteUntilMs = 0L; s.muteUses = 0 }

    /** Misión 1 cumplida (QR/NFC): se silencia y se agenda el Wake-Up Check a los 7–10 min. */
    fun scanSuccess(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.RINGING) return
        val delayMin = Random.nextInt(AppRulesConfig.WAKE_CHECK_MIN_DELAY_MIN, AppRulesConfig.WAKE_CHECK_MAX_DELAY_MIN + 1)
        s.checkAtMs = System.currentTimeMillis() + delayMin * 60_000L
        resetMute(s)
        s.phase = AlarmPhase.CHECK_WAIT
        AlarmScheduler.scheduleCheck(ctx, s.checkAtMs)
        AlarmService.sync(ctx)
    }

    /** Llega la hora del Wake-Up Check: cuenta atrás de 90 s. */
    fun startCheck(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase == AlarmPhase.RINGING) return
        s.checkDeadlineMs = System.currentTimeMillis() + AppRulesConfig.WAKE_CHECK_COUNTDOWN_SEC * 1000L
        s.phase = AlarmPhase.CHECK_ACTIVE
        AlarmService.sync(ctx)
    }

    fun confirmAwake(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.CHECK_ACTIVE) return
        s.phase = AlarmPhase.IDLE
        AlarmService.sync(ctx)
    }

    /** No respondió a tiempo: vuelve la alarma completa. */
    fun escalate(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase == AlarmPhase.CHECK_ACTIVE) { resetMute(s); s.phase = AlarmPhase.RINGING }
    }

    /** Tras reinicio: reprograma todo y reanuda lo que estuviera pendiente. */
    fun restoreAfterBoot(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        AlarmScheduler.scheduleDaily(ctx)
        when (s.phase) {
            AlarmPhase.RINGING, AlarmPhase.CHECK_ACTIVE -> AlarmService.sync(ctx)
            AlarmPhase.CHECK_WAIT -> AlarmScheduler.scheduleCheck(ctx, maxOf(s.checkAtMs, System.currentTimeMillis() + 3_000L))
            AlarmPhase.IDLE -> {}
        }
    }
}
