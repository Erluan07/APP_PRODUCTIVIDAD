package com.kronos.focus

import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.TaskImport
import com.kronos.focus.domain.sleep.Epoch
import com.kronos.focus.domain.sleep.SleepModel
import com.kronos.focus.domain.sleep.SleepParams
import com.kronos.focus.domain.stats.DayStat
import com.kronos.focus.domain.stats.StreakCalc
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.services.AlarmScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class RulesTest {
    private val launchers = setOf("com.miui.home")
    private val imes = setOf("com.google.android.inputmethod.latin")
    private fun allowed(p: String) = AppRulesConfig.isAllowed(p, launchers, imes)

    @Test fun whitelistAllowsEssentialApps() {
        listOf("com.whatsapp", "com.whatsapp.w4b", "com.spotify.music", "com.kronos.focus",
            "com.google.android.dialer", "com.google.android.apps.messaging", "com.miui.home",
            "com.google.android.inputmethod.latin", "com.android.systemui").forEach { assertTrue(it, allowed(it)) }
    }

    @Test fun distractionsAndSettingsAreBlocked() {
        listOf("com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube",
            "com.android.chrome", "com.android.settings", "com.samsung.android.settings",
            "com.google.android.settings.intelligence", "com.android.vending").forEach { assertFalse(it, allowed(it)) }
    }

    @Test fun parseHm() {
        assertEquals(8 * 60, AgendaValidator.parseHm("8"))
        assertEquals(8 * 60 + 30, AgendaValidator.parseHm("08:30"))
        assertEquals(8 * 60 + 30, AgendaValidator.parseHm("0830"))
        assertEquals(8 * 60 + 30, AgendaValidator.parseHm("830"))
        assertEquals(16 * 60 + 30, AgendaValidator.parseHm("16.30"))
        assertNull(AgendaValidator.parseHm("24:00"))
        assertNull(AgendaValidator.parseHm("12:75"))
        assertNull(AgendaValidator.parseHm("abc"))
        assertNull(AgendaValidator.parseHm(""))
    }

    private fun at(h: Int, m: Int) = AgendaValidator.todayAt(h * 60 + m, base)
    private val base: Long = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, 6, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun block(n: String, s1: Int, s2: Int, e1: Int, e2: Int) = AgendaBlock(n, at(s1, s2), at(e1, e2))

    @Test fun validAgenda() {
        val b = listOf(block("Estudiar", 8, 0, 10, 0), block("Gimnasio", 16, 0, 17, 30))
        assertNull(AgendaValidator.validate(b, base))
    }

    @Test fun rejectsEmptyOverlapShortPastAndOverTotal() {
        assertNotNull(AgendaValidator.validate(emptyList(), base))
        assertNotNull(AgendaValidator.validate(listOf(block("A", 8, 0, 10, 0), block("B", 9, 30, 11, 0)), base))
        assertNotNull(AgendaValidator.validate(listOf(block("A", 8, 0, 8, 10)), base))
        assertNotNull(AgendaValidator.validate(listOf(block("A", 8, 0, 6, 2)), base))
        assertNotNull(AgendaValidator.validate(listOf(block(" ", 8, 0, 10, 0)), base))
        assertNotNull(AgendaValidator.validate(listOf(block("A", 6, 0, 12, 0)), base)) // 6 h > máx. por bloque
        val total = listOf(block("A", 6, 30, 10, 30), block("B", 11, 0, 15, 0), block("C", 15, 30, 19, 30), block("D", 20, 0, 21, 0))
        assertNotNull(AgendaValidator.validate(total, base)) // 9 h > máx. total
    }

    @Test fun alarmDeadlinePerDay() {
        fun cal(day: Int, h: Int, m: Int = 0) = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, day, h, m, 0); set(Calendar.MILLISECOND, 0) }
        fun at(c: Calendar): Long { val (h, m) = AppRulesConfig.alarmAt(c.get(Calendar.DAY_OF_WEEK))!!; return Calendar.getInstance().apply { timeInMillis = c.timeInMillis; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.timeInMillis }
        val monday = cal(12, 6)            // 12-oct-2026 es lunes
        assertEquals(at(monday), AlarmScheduler.nextDeadline(monday.timeInMillis, false))
        val mondayLate = cal(12, 23)
        val tuesday = cal(13, 6)
        assertEquals(at(tuesday), AlarmScheduler.nextDeadline(mondayLate.timeInMillis, false))
        assertEquals(at(tuesday), AlarmScheduler.nextDeadline(monday.timeInMillis, true))
        val fridayLate = cal(16, 23)       // viernes -> sábado
        assertEquals(Calendar.SATURDAY, Calendar.getInstance().apply { timeInMillis = AlarmScheduler.nextDeadline(fridayLate.timeInMillis, false) }.get(Calendar.DAY_OF_WEEK))
    }

    @Test fun sleepModeStrictWhitelist() {
        assertFalse(AppRulesConfig.isAllowed("com.whatsapp", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.google.android.dialer", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.spotify.music", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.miui.home", launchers, imes, strict = true))
        assertFalse(AppRulesConfig.isAllowed("com.google.android.apps.messaging", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.google.android.apps.messaging", launchers, imes, strict = false))
    }

    private fun quiet() = List(6) { Epoch(0, false, false) }
    private fun stirring() = List(6) { Epoch(2, false, false) }

    @Test fun sleepModelWakesOnlyWhenStirringInsideWindow() {
        val p = SleepParams()
        val lightPhase = 5.0 * 90 - 10      // final de un ciclo (sueño ligero probable)
        val deepPhase = 5.0 * 90 + 20       // inicio de ciclo (sueño profundo probable)
        assertFalse(SleepModel.shouldWake(quiet(), lightPhase, 10.0, p))
        assertTrue(SleepModel.shouldWake(stirring(), lightPhase, 10.0, p))
        assertFalse(SleepModel.shouldWake(stirring(), lightPhase, 40.0, p))   // fuera de la ventana de 25 min
        assertFalse(SleepModel.shouldWake(stirring(), lightPhase, -1.0, p))   // la alarma ya pasó
        assertTrue(SleepModel.cycleLightness(lightPhase) > SleepModel.cycleLightness(deepPhase))
    }

    @Test fun sleepModelUsesSoundWhenMicIsValid() {
        val loud = List(6) { Epoch(1, true, true) }
        val silentMic = List(6) { Epoch(1, false, true) }
        assertTrue(SleepModel.activity(loud) > SleepModel.activity(silentMic))
    }

    @Test fun sleepModelLearnsFromRating() {
        val p = SleepParams(25, 0.55f)
        assertEquals(p, SleepModel.adjust(p, 5, 20))
        val early = SleepModel.adjust(p, 1, 20)
        assertTrue(early.windowMin < p.windowMin && early.threshold > p.threshold)
        val late = SleepModel.adjust(p, 2, 0)
        assertTrue(late.windowMin > p.windowMin && late.threshold < p.threshold)
        var q = p
        repeat(20) { q = SleepModel.adjust(q, 1, 20) }
        assertTrue(q.windowMin >= 5 && q.threshold <= 0.8f)
    }

    @Test fun streaks() {
        val ok = DayStat(2, 2, 0); val bad = DayStat(2, 1, 0)
        val days = mapOf(10L to ok, 11L to ok, 12L to bad, 13L to ok, 14L to ok, 15L to ok)
        assertEquals(3, StreakCalc.current(days, 15L))
        assertEquals(3, StreakCalc.best(days))
        assertEquals(3, StreakCalc.current(days, 16L)) // hoy aún sin cumplir: cuenta hasta ayer
        assertEquals(0, StreakCalc.current(mapOf(15L to bad, 14L to bad), 15L))
    }
}
