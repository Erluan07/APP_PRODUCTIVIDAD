package com.kronos.focus.services

import android.content.Context
import com.kronos.focus.data.AlarmPhase
import com.kronos.focus.data.AlarmStateStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.data.AgendaStore
import com.kronos.focus.data.RoutineStore
import com.kronos.focus.data.SleepLogStore
import com.kronos.focus.data.StatsStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.MustRules
import com.kronos.focus.domain.agenda.MustTask
import kotlin.random.Random

/** Máquina de estados de la alarma. Toda transición pasa por aquí. */
object AlarmFlow {

    /** Hora límite o Smart Wake-Up: empieza a sonar. */
    fun ring(ctx: Context, smart: Boolean = false, stamp: String? = null) {
        val s = AlarmStateStore.get(ctx)
        run {
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
        }
        AlarmService.sync(ctx)
    }

    /** Solo Modo prueba: suena la alarma ahora, con la rutina armada, sin consumir la alarma real del día. */
    fun ringNow(ctx: Context) {
        if (!TestMode.active(ctx)) return
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.IDLE) return
        val routine = RoutineStore.get(ctx)
        routine.armed = routine.steps().isNotEmpty()
        s.ringNote = "Prueba de la alarma."
        resetMute(s)
        s.phase = AlarmPhase.RINGING
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
        MusicResume.play(ctx) // retoma Spotify para levantarte con música
        val routine = RoutineStore.get(ctx)
        // Rutina armada al irte a dormir (o ya empezada hoy): foto de cada paso, con la música bloqueada.
        if (routine.steps().isNotEmpty() && (routine.armed || routine.inProgress(AlarmScheduler.epochDay()))) {
            routine.begin(AlarmScheduler.epochDay())
            if (!routine.allDone()) {
                s.routineDeadlineMs = System.currentTimeMillis() + AppRulesConfig.ROUTINE_TIMEOUT_MIN * 60_000L
                s.phase = AlarmPhase.ROUTINE
                AlarmService.sync(ctx)
                return
            }
        }
        afterRoutine(ctx, s)
    }

    /** Rutina terminada (o no armada): sigue al ritual de agenda o, si ya se hizo hoy, al monitoreo de pasos. */
    private fun afterRoutine(ctx: Context, s: AlarmStateStore) {
        if (AgendaStore.get(ctx).committedDay() == AlarmScheduler.epochDay()) {
            startWatch(ctx, s)
        } else {
            s.ritualDeadlineMs = System.currentTimeMillis() + AppRulesConfig.RITUAL_TIMEOUT_MIN * 60_000L
            s.phase = AlarmPhase.RITUAL
            AlarmService.sync(ctx)
        }
    }

    /** Se subió la evidencia de un paso; con el último, la música queda libre y sigue el flujo. */
    fun routineStepDone(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.ROUTINE || !RoutineStore.get(ctx).allDone()) return
        s.routineDeadlineMs = 0L
        afterRoutine(ctx, s)
    }

    /** No terminó la rutina a tiempo: vuelve la alarma completa. */
    fun escalateRoutine(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.ROUTINE) return
        resetMute(s)
        s.ringNote = "No terminaste la rutina a tiempo. Escanea otra vez y retómala donde la dejaste."
        s.phase = AlarmPhase.RINGING
        AlarmService.sync(ctx)
    }

    /** Misión 2: valida y fija la agenda del día; programa el búnker y el Wake-Up Check. Devuelve un error o null. */
    fun commitAgenda(ctx: Context, blocks: List<AgendaBlock>, flexNames: List<String> = emptyList(), musts: List<MustTask> = emptyList()): String? {
        val s = AlarmStateStore.get(ctx)
        if (s.phase != AlarmPhase.RITUAL) return "No hay un ritual activo."
        AgendaValidator.validate(blocks, System.currentTimeMillis(), flexNames.size + musts.size)?.let { return it }
        val agenda = AgendaStore.get(ctx)
        agenda.commit(blocks, flexNames, AlarmScheduler.epochDay())
        // Las obligatorias se suman a las que ya estuvieran pendientes (nunca se quitan) y bloquean desde su hora de inicio.
        musts.forEach { if (MustRules.validateAdd(agenda.musts(), it.name) == null) agenda.addMust(it) }
        StatsStore.get(ctx).recordCommit(AlarmScheduler.epochDay(), blocks.size + flexNames.distinct().size + musts.size)
        AlarmScheduler.scheduleBlocks(ctx)
        if (BunkerStateStore.get(ctx).isActive()) BunkerWatchdogService.start(ctx)
        startWatch(ctx, s)
        return null
    }

    /** No completó el ritual a tiempo: vuelve la alarma completa. */
    fun escalateRitual(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase == AlarmPhase.RITUAL) { resetMute(s); s.phase = AlarmPhase.RINGING }
    }

    /** Ya no hay "¿sigues despierto?": tras la rutina/ritual solo corre el monitoreo de pasos. */
    private fun startWatch(ctx: Context, s: AlarmStateStore) {
        resetMute(s)
        s.phase = AlarmPhase.IDLE
        AlarmService.sync(ctx)
        AwakeWatchService.start(ctx, System.currentTimeMillis() + AppRulesConfig.AWAKE_WATCH_MIN * 60_000L)
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
        if (!AwakeWatchService.running) AwakeWatchService.start(ctx) // respaldo: normalmente ya está vigilando desde el escaneo
    }

    /** Te quedaste sin usar el teléfono tras decir "estoy despierto": suena otra vez de inmediato. */
    fun relapse(ctx: Context) {
        val s = AlarmStateStore.get(ctx)
        if (s.phase == AlarmPhase.RINGING || s.phase == AlarmPhase.RITUAL || s.phase == AlarmPhase.ROUTINE) return
        resetMute(s)
        s.ringNote = "Parece que te volviste a la cama: no caminaste (${AppRulesConfig.AWAKE_MIN_STEPS} pasos cada ${AppRulesConfig.AWAKE_WINDOW_MIN} min). Levántate y vuelve a escanear."
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
            AlarmPhase.RINGING, AlarmPhase.ROUTINE, AlarmPhase.RITUAL, AlarmPhase.CHECK_ACTIVE -> AlarmService.sync(ctx)
            AlarmPhase.CHECK_WAIT -> AlarmScheduler.scheduleCheck(ctx, maxOf(s.checkAtMs, System.currentTimeMillis() + 3_000L))
            AlarmPhase.IDLE -> {}
        }
    }
}
