package com.kronos.focus

import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
import com.kronos.focus.domain.agenda.TaskImport
import com.kronos.focus.domain.sleep.Epoch
import com.kronos.focus.domain.sleep.SleepModel
import com.kronos.focus.domain.sleep.SleepParams
import com.kronos.focus.domain.stats.DayStat
import com.kronos.focus.domain.stats.StreakCalc
import com.kronos.focus.domain.agenda.MustRules
import com.kronos.focus.domain.agenda.MustTask
import com.kronos.focus.domain.limits.LimitBlock
import com.kronos.focus.domain.limits.UsageLimits
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.domain.alarm.AlarmEntry
import com.kronos.focus.domain.alarm.AlarmTimes
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

    private fun cal(day: Int, h: Int, m: Int = 0) = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, day, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val weekdays = AlarmEntry(1, 7, 0, 0b0011111, true, "Trabajo")   // lunes a viernes
    private val weekend = AlarmEntry(2, 9, 0, 0b1100000, true, "Descanso")   // sábado y domingo

    @Test fun nextAlarmPicksTheRightDayAndTime() {
        val alarms = listOf(weekdays, weekend)
        // 12-oct-2026 es lunes
        assertEquals(cal(12, 7), AlarmTimes.next(alarms, cal(12, 6), null)!!.timeMs)
        assertEquals(cal(13, 7), AlarmTimes.next(alarms, cal(12, 8), null)!!.timeMs)
        assertEquals(cal(17, 9), AlarmTimes.next(alarms, cal(16, 23), null)!!.timeMs)   // viernes noche -> sábado 9:00
        assertEquals(cal(19, 7), AlarmTimes.next(alarms, cal(18, 10), null)!!.timeMs)   // domingo -> lunes 7:00
    }

    @Test fun ringedOccurrenceIsSkippedAndDisabledIgnored() {
        val first = AlarmTimes.next(listOf(weekdays), cal(12, 6), null)!!
        assertEquals(cal(13, 7), AlarmTimes.next(listOf(weekdays), cal(12, 6), first.stamp)!!.timeMs)
        assertNull(AlarmTimes.next(listOf(weekdays.copy(enabled = false)), cal(12, 6), null))
        assertNull(AlarmTimes.next(emptyList(), cal(12, 6), null))
        assertNull(AlarmTimes.next(listOf(weekdays.copy(days = 0)), cal(12, 6), null))
    }

    @Test fun twoAlarmsSameDayBothRing() {
        val a = AlarmEntry(1, 7, 0, AlarmTimes.ALL_DAYS, true, "")
        val b = AlarmEntry(2, 7, 30, AlarmTimes.ALL_DAYS, true, "")
        val first = AlarmTimes.next(listOf(a, b), cal(12, 6), null)!!
        assertEquals(1, first.alarm.id)
        val second = AlarmTimes.next(listOf(a, b), cal(12, 6), first.stamp)!!
        assertEquals(2, second.alarm.id)
        assertEquals(cal(12, 7, 30), second.timeMs)
    }

    @Test fun alarmLockedWhenRingingSoon() {
        assertTrue(AlarmTimes.isLocked(weekdays, cal(12, 5), null, 3))     // suena en 2 h
        assertFalse(AlarmTimes.isLocked(weekdays, cal(12, 2), null, 3))    // suena en 5 h
        assertFalse(AlarmTimes.isLocked(weekdays.copy(enabled = false), cal(12, 5), null, 3))
    }

    @Test fun daysText() {
        assertEquals("Todos los días", AlarmTimes.daysText(AlarmTimes.ALL_DAYS))
        assertEquals("Lunes a viernes", AlarmTimes.daysText(0b0011111))
        assertEquals("L X V", AlarmTimes.daysText(0b0010101))
    }

    @Test fun sleepModeStrictWhitelist() {
        assertFalse(AppRulesConfig.isAllowed("com.whatsapp", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.google.android.dialer", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.spotify.music", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.miui.home", launchers, imes, strict = true))
        assertFalse(AppRulesConfig.isAllowed("com.google.android.apps.messaging", launchers, imes, strict = true))
        assertTrue(AppRulesConfig.isAllowed("com.google.android.apps.messaging", launchers, imes, strict = false))
    }

    @Test fun chosenAppsReplaceDefaultsButPhoneStaysAllowed() {
        val chosen = setOf("com.example.notes", "com.whatsapp")
        fun ok(p: String) = AppRulesConfig.isAllowed(p, launchers, imes, userApps = chosen)
        assertTrue(ok("com.example.notes"))
        assertTrue(ok("com.whatsapp"))
        assertTrue(ok("com.whatsapp.w4b"))                 // misma familia que una app elegida
        assertFalse(ok("com.spotify.music"))               // no elegida: bloqueada aunque estuviera en los valores por defecto
        assertTrue(ok("com.google.android.dialer"))        // el Teléfono siempre
        assertTrue(ok("com.android.systemui"))
        assertFalse(ok("com.android.settings"))
        assertFalse(ok("com.instagram.android"))
        assertFalse(AppRulesConfig.isAllowed("com.example.notes", launchers, imes, strict = true, userApps = chosen))
    }

    @Test fun helperScreensExcludeBrowsersAndStores() {
        assertTrue("com.sec.android.app.camera" in AppRulesConfig.APP_HELPERS)
        assertFalse("com.android.chrome" in AppRulesConfig.APP_HELPERS)
        assertFalse("com.android.vending" in AppRulesConfig.APP_HELPERS)
    }

    @Test fun usageLimitBlocksAndTightensOnly() {
        val now = 1_000_000L
        assertNull(UsageLimits.blockFor(10 * 60_000L, 30, 0L, now))
        assertEquals(LimitBlock.DailyExhausted, UsageLimits.blockFor(30 * 60_000L, 30, 0L, now))
        assertEquals(LimitBlock.Cooldown(now + 5), UsageLimits.blockFor(0L, 30, now + 5, now))
        assertNull(UsageLimits.blockFor(0L, 30, now - 1, now))                 // el enfriamiento ya pasó
        assertTrue(UsageLimits.canSet(null, 30))
        assertTrue(UsageLimits.canSet(30, 20))                                  // endurecer: sí
        assertFalse(UsageLimits.canSet(30, 45))                                 // aflojar: no
        assertFalse(UsageLimits.canSet(null, 1))                                // por debajo del mínimo
        assertFalse(UsageLimits.continuousExceeded(14 * 60_000L))
        assertTrue(UsageLimits.continuousExceeded(15 * 60_000L))
    }

    @Test fun mustTaskBlocksFromStartUntilDone() {
        val now = 10_000L
        val list = listOf(MustTask("Gimnasio", now + 5_000), MustTask("Ya empezada", now - 1, done = false), MustTask("Hecha", now - 9, done = true))
        assertEquals("Ya empezada", MustRules.activeAt(list, now)?.name)               // la futura no bloquea todavía; la hecha tampoco
        assertNull(MustRules.activeAt(listOf(MustTask("Gimnasio", now + 5_000)), now))
        assertEquals("Gimnasio", MustRules.activeAt(listOf(MustTask("Gimnasio", now + 5_000)), now + 5_000)?.name)
        assertNull(MustRules.activeAt(listOf(MustTask("Gimnasio", now - 1, done = true)), now))
        assertNotNull(MustRules.validateAdd(list, "  "))
        assertNotNull(MustRules.validateAdd(list, "gimnasio"))                           // repetida y pendiente
        assertNull(MustRules.validateAdd(list, "Hecha"))                                 // una ya hecha se puede volver a pedir
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
