package com.kronos.focus.services

import android.content.Context
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.data.StatsStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import kotlin.random.Random

/** Máquina de estados de la alarma. Toda transición pasa por aquí. */
object AlarmFlow {

    /** Hora límite o Smart Wake-Up: empieza a sonar. [test] no consume la alarma real del día. */
    fun ring(ctx: Context, test: Boolean = false, smart: Boolean = false, stamp: String? = null) {
        val s = AlarmStateStore.get(ctx)
        if (!test) {
            val now = System.currentTimeMillis()
            val occ = AlarmScheduler.nextOccurrence(ctx, now)
            val key = stamp ?: occ?.stamp
            if (key != null && s.lastRingStamp == key) return
            val early = if (smart) (((occ?.timeMs ?: now) - now) / 60_000L).toInt().coerceAtLeast(0) else 0
            SleepLogStore.get(ctx).recordWake(now, early, smart, AlarmScheduler.epochDay())
            SleepTrackService.stop(ctx)
            BunkerStateStore.get(ctx).endSleep()
            s.lastRingStamp = key
            s.lastRingDay = AlarmScheduler.epochDay()
            AwakeWatchService.stop(ctx)
            val hm = occ?.alarm?.hm()
            s.ringNote = when {
                hm == null -> ""
                smart && early > 0 -> "Te desperté $early min antes de tu alarma de las $hm porque estabas en sueño ligero."
                else -> "Alarma de las $hm."
            }
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

    /** Misión 1 cumplida (QR/NFC): pasa al ritual de agenda (Misión 2), o directo al check si ya se hizo hoy. */
    fun scanSuccess(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.RINGING) return
        resetMute(s)
        if (AgendaStore.get(ctx).committedDay() == AlarmScheduler.epochDay()) {
            toCheckWait(ctx, s)
        } else {
            s.ritualDeadlineMs = System.currentTimeMillis() + AppRulesConfig.RITUAL_TIMEOUT_MIN * 60_000L
            s.phase = AlarmPhase.RITUAL
            AlarmService.sync(ctx)
        }
    }

    /** Misión 2: valida y fija la agenda del día; programa el búnker y el Wake-Up Check. Devuelve un error o null. */
    fun commitAgenda(ctx: Context, blocks: List<AgendaBlock>, flexNames: List<String> = emptyList()): String? {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.RITUAL) return "No hay un ritual activo."
        AgendaValidator.validate(blocks, System.currentTimeMillis(), flexNames.size)?.let { return it }
        AgendaStore.get(ctx).commit(blocks, flexNames, AlarmScheduler.epochDay())
        StatsStore.get(ctx).recordCommit(AlarmScheduler.epochDay(), blocks.size + flexNames.distinct().size)
        AlarmScheduler.scheduleBlocks(ctx)
        if (BunkerStateStore.get(ctx).isActive()) BunkerWatchdogService.start(ctx)
        toCheckWait(ctx, s)
        return null
    }

    /** No completó el ritual a tiempo: vuelve la alarma completa. */
    fun escalateRitual(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase == AlarmPhase.RITUAL) { resetMute(s); s.phase = AlarmPhase.RINGING }
    }

    private fun toCheckWait(ctx: Context, s: AlarmStateStore) {
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
        AwakeWatchService.start(ctx) // vigila que no te vuelvas a dormir
    }

    /** Te quedaste sin usar el teléfono tras decir "estoy despierto": suena otra vez de inmediato. */
    fun relapse(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.IDLE) return
        resetMute(s)
        s.ringNote = "Parece que te volviste a dormir: no usaste ni moviste el teléfono en ${AppRulesConfig.AWAKE_IDLE_MIN} min."
        s.phase = AlarmPhase.RINGING
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
        AlarmScheduler.scheduleBlocks(ctx)
        when (s.phase) {
            AlarmPhase.RINGING, AlarmPhase.RITUAL, AlarmPhase.CHECK_ACTIVE -> AlarmService.sync(ctx)
            AlarmPhase.CHECK_WAIT -> AlarmScheduler.scheduleCheck(ctx, maxOf(s.checkAtMs, System.currentTimeMillis() + 3_000L))
            AlarmPhase.IDLE -> {}
        }
    }
}
