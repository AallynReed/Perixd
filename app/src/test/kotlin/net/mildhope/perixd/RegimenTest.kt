package net.mildhope.perixd

import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Notifications
import net.mildhope.perixd.domain.Dose
import net.mildhope.perixd.domain.PillState
import net.mildhope.perixd.domain.pillState
import net.mildhope.perixd.domain.dayIndex
import net.mildhope.perixd.domain.doseNumber
import net.mildhope.perixd.domain.doseOn
import net.mildhope.perixd.domain.regimen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RegimenTest {
    private val start = LocalDate.parse("2026-01-05")
    private fun day(i: Int) = start.plusDays(i.toLong())

    @Test
    fun pillWithBreak() {
        val m = Method(type = MethodType.PILL, start = start)
        assertEquals(28, m.regimen()!!.length)
        assertEquals(21..27, m.regimen()!!.hormoneFree)
        assertEquals(Dose.PILL, m.doseOn(day(20)))
        assertNull(m.doseOn(day(21)))
        assertEquals(Dose.PILL, m.doseOn(day(28)))
        assertEquals(12 to 21, m.doseNumber(day(11)))
        assertNull(m.doseOn(day(-1)))
    }

    @Test
    fun pillWithPlacebo() {
        val m = Method(type = MethodType.PILL, start = start, activePills = 24, placeboPills = 4, breakDays = 0)
        assertEquals(24..27, m.regimen()!!.hormoneFree)
        assertEquals(Dose.PLACEBO, m.doseOn(day(25)))
        assertEquals(2 to 4, m.doseNumber(day(25)))
    }

    @Test
    fun continuousPillHasNoHormoneFreeDays() {
        val m = Method(type = MethodType.PILL, start = start, activePills = 28, breakDays = 0)
        assertNull(m.regimen()!!.hormoneFree)
        assertEquals(Dose.PILL, m.doseOn(day(27)))
    }

    @Test
    fun patchChangesWeeklyThenHasAFreeWeek() {
        val m = Method(type = MethodType.PATCH, start = start)
        assertEquals(listOf(Dose.PATCH_APPLY, Dose.PATCH_CHANGE, Dose.PATCH_CHANGE, Dose.PATCH_REMOVE), listOf(0, 7, 14, 21).map { m.doseOn(day(it)) })
        assertEquals(2 to 3, m.doseNumber(day(7)))
        assertNull(m.doseOn(day(1)))
    }

    @Test
    fun ringAndInjection() {
        val ring = Method(type = MethodType.RING, start = start)
        assertEquals(Dose.RING_INSERT, ring.doseOn(day(28)))
        assertEquals(Dose.RING_REMOVE, ring.doseOn(day(21)))
        assertEquals(Dose.RING_CHANGE, ring.copy(hormoneFreeWeek = false).doseOn(day(28)))
        val shot = Method(type = MethodType.INJECTION, start = start, injectionWeeks = 12)
        assertEquals(Dose.INJECTION, shot.doseOn(day(84)))
        assertNull(shot.doseOn(day(83)))
        assertEquals(5, shot.dayIndex(day(89)))
    }

    @Test
    fun packShowsHowTheRealPackShouldLookNow() {
        val m = Method(type = MethodType.PILL, start = start, trackedFrom = start.plusDays(2))
        val n = Notifications(time = LocalTime.of(20, 0))
        val zone = ZoneId.of("Europe/Lisbon")
        val day = start.plusDays(5)
        fun at(time: String) = ZonedDateTime.of(day, LocalTime.parse(time), zone)
        assertEquals(PillState.IN_PACK, pillState(m, emptyMap(), n, day, at("19:00")))
        assertEquals(PillState.GHOST, pillState(m, emptyMap(), n, day, at("21:00")))
        assertEquals(PillState.TAKEN, pillState(m, mapOf(day to 1L), n, day, at("19:00")))
        assertEquals(PillState.GHOST, pillState(m, emptyMap(), n, day.minusDays(1), at("09:00")))
        assertEquals(PillState.TAKEN, pillState(m, emptyMap(), n, start, at("09:00")))
    }

    @Test
    fun methodsWithoutDoses() {
        assertNull(Method(type = MethodType.NONE, start = start).regimen())
        assertNull(Method(type = MethodType.LONG_ACTING, start = start).doseOn(day(0)))
    }
}
