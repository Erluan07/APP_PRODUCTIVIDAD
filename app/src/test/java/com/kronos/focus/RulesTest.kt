package com.kronos.focus

import com.kronos.focus.domain.agenda.AgendaBlock
import com.kronos.focus.domain.agenda.AgendaValidator
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

    @Test fun alarmDeadline() {
        fun cal(h: Int) = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, h, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val seven = cal(AppRulesConfig.ALARM_HOUR).let { it + AppRulesConfig.ALARM_MINUTE * 60_000L }
        assertEquals(seven, AlarmScheduler.nextDeadline(cal(AppRulesConfig.ALARM_HOUR - 1), false))
        assertEquals(seven + 86_400_000L, AlarmScheduler.nextDeadline(cal(AppRulesConfig.ALARM_HOUR + 1), false))
        assertEquals(seven + 86_400_000L, AlarmScheduler.nextDeadline(cal(AppRulesConfig.ALARM_HOUR - 1), true))
    }
}
