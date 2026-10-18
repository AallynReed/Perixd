package net.mildhope.perixd

import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Notifications
import net.mildhope.perixd.data.Settings
import net.mildhope.perixd.data.withDose
import net.mildhope.perixd.domain.BleedSoon
import net.mildhope.perixd.domain.DoseAction
import net.mildhope.perixd.domain.DoseAlert
import net.mildhope.perixd.domain.DoseSoon
import net.mildhope.perixd.domain.DueDose
import net.mildhope.perixd.domain.Dose
import net.mildhope.perixd.domain.LogReminder
import net.mildhope.perixd.domain.Runtime
import net.mildhope.perixd.domain.alerts
import net.mildhope.perixd.domain.dueDose
import net.mildhope.perixd.domain.nextWake
import net.mildhope.perixd.domain.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertsTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val start = LocalDate.parse("2026-01-05")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    private fun pillData(n: Notifications = Notifications(periodDaysBefore = 0)) = Data(
        settings = Settings(method = Method(type = MethodType.PILL, start = start, trackedFrom = start), notifications = n),
    )

    @Test
    fun doseIsDueOnceItsTimePasses() {
        val data = pillData()
        assertNull(dueDose(data, at("2026-01-05", "20:59")))
        assertEquals(DueDose(LocalDate.parse("2026-01-05"), Dose.PILL), dueDose(data, at("2026-01-05", "21:00")))
        assertEquals(LocalDate.parse("2026-01-05"), dueDose(data, at("2026-01-06", "08:00"))!!.day)
        assertNull(dueDose(data.withDose(LocalDate.parse("2026-01-05"), 1), at("2026-01-06", "08:00")))
    }

    @Test
    fun lastPillStaysDueThroughTheBreakUntilTaken() {
        assertEquals(LocalDate.parse("2026-01-25"), dueDose(pillData(), at("2026-01-30", "12:00"))!!.day)
    }

    @Test
    fun nothingDueBeforeTrackingStarted() {
        val data = pillData().let { it.copy(settings = it.settings.copy(method = it.settings.method.copy(trackedFrom = LocalDate.parse("2026-01-10")))) }
        assertNull(dueDose(data, at("2026-01-09", "23:00")))
    }

    @Test
    fun weekendTimeApplies() {
        val data = pillData(Notifications(time = LocalTime.of(8, 0), weekendTime = LocalTime.of(11, 0), periodDaysBefore = 0))
        assertEquals(at("2026-01-10", "11:00"), nextWake(data, at("2026-01-09", "09:00"), Runtime()))
    }

    @Test
    fun remindersPauseDuringTheBreakAndWarnBeforeTheNewPack() {
        val data = pillData()
        val wake = nextWake(data, at("2026-01-25", "21:30"), Runtime())
        assertEquals(at("2026-02-01", "09:00"), wake)
        val notices = alerts(data, at("2026-01-25", "21:30"), at("2026-02-03", "00:00"))
        assertEquals(listOf(DoseSoon::class, DoseAlert::class), notices.map { it::class })
        assertEquals(at("2026-02-02", "21:00"), notices[1].at)
    }

    @Test
    fun repeatsUntilDoneAndSkipsSnoozedTime() {
        val data = pillData(Notifications(repeatMinutes = 30, periodDaysBefore = 0, newPackNotice = false))
        val repeats = alerts(data, at("2026-01-05", "21:00"), at("2026-01-05", "23:00")).map { it.at.toLocalTime().toString() }
        assertEquals(listOf("21:30", "22:00", "22:30", "23:00"), repeats)
        val snoozed = Runtime(snoozedUntil = at("2026-01-05", "22:15").toInstant(), snoozedDay = LocalDate.parse("2026-01-05"))
        val kinds = alerts(data, at("2026-01-05", "21:00"), at("2026-01-05", "23:00"), snoozed).map { (it as DoseAlert).kind to it.at.toLocalTime().toString() }
        assertEquals(listOf(DoseAlert.Kind.SNOOZE_END to "22:15", DoseAlert.Kind.REPEAT to "22:30", DoseAlert.Kind.REPEAT to "23:00"), kinds)
    }

    @Test
    fun repeatsStopAtTheNextDose() {
        val data = pillData(Notifications(repeatMinutes = 360, periodDaysBefore = 0))
        val times = alerts(data, at("2026-01-05", "21:00"), at("2026-01-06", "21:00")).map { it.at.toString() }
        assertEquals(listOf(at("2026-01-06", "03:00"), at("2026-01-06", "09:00"), at("2026-01-06", "15:00"), at("2026-01-06", "21:00")).map { it.toString() }, times)
    }

    @Test
    fun planAlertsWhenTheReminderFiresThenStaysSilent() {
        val data = pillData()
        val fired = plan(data, Runtime(lastCheck = at("2026-01-05", "20:00").toInstant()), at("2026-01-05", "21:00"))
        assertEquals(DoseAction.POST_ALERT, fired.doseAction)
        val again = plan(data, fired.runtime, at("2026-01-05", "21:05"))
        assertEquals(DoseAction.POST_SILENT, again.doseAction)
        assertEquals(at("2026-01-06", "21:00"), again.nextWake)
    }

    @Test
    fun planCancelsWhenTakenAndKeepsDismissableRemindersAlone() {
        val day = LocalDate.parse("2026-01-05")
        val now = at("2026-01-05", "21:10")
        val runtime = Runtime(lastCheck = at("2026-01-05", "21:05").toInstant())
        assertEquals(DoseAction.CANCEL, plan(pillData().withDose(day, 1), runtime, now).doseAction)
        assertEquals(DoseAction.KEEP, plan(pillData(Notifications(persistent = false, periodDaysBefore = 0)), runtime, now).doseAction)
    }

    @Test
    fun planHidesWhileSnoozedAndAlertsWhenSnoozeEnds() {
        val day = LocalDate.parse("2026-01-05")
        val snooze = Runtime(lastCheck = at("2026-01-05", "21:01").toInstant(), snoozedUntil = at("2026-01-05", "21:15").toInstant(), snoozedDay = day)
        val during = plan(pillData(), snooze, at("2026-01-05", "21:05"))
        assertEquals(DoseAction.CANCEL, during.doseAction)
        assertEquals(at("2026-01-05", "21:15"), during.nextWake)
        val after = plan(pillData(), during.runtime, at("2026-01-05", "21:15"))
        assertEquals(DoseAction.POST_ALERT, after.doseAction)
        assertNull(after.runtime.snoozedUntil)
    }

    @Test
    fun missedAlertsOlderThanTheCatchUpWindowAreDropped() {
        val data = pillData()
        val p = plan(data, Runtime(lastCheck = at("2026-01-05", "08:00").toInstant()), at("2026-01-06", "10:00"))
        assertEquals(DoseAction.POST_SILENT, p.doseAction)
        val boot = plan(data, Runtime(lastCheck = at("2026-01-05", "20:00").toInstant()), at("2026-01-06", "07:00"))
        assertEquals(DoseAction.POST_ALERT, boot.doseAction)
    }

    @Test
    fun disabledRemindersNeverShowTheDose() {
        val p = plan(pillData(Notifications(doseEnabled = false, periodDaysBefore = 0)), Runtime(), at("2026-01-05", "22:00"))
        assertEquals(DoseAction.CANCEL, p.doseAction)
    }

    @Test
    fun springForwardMovesAReminderInTheGap() {
        val data = Data(settings = Settings(
            method = Method(type = MethodType.PILL, start = LocalDate.parse("2026-03-20"), trackedFrom = LocalDate.parse("2026-03-20")),
            notifications = Notifications(time = LocalTime.of(1, 30), periodDaysBefore = 0),
        ))
        assertEquals(LocalTime.of(2, 30), nextWake(data, at("2026-03-28", "12:00"), Runtime())!!.toLocalTime())
    }

    @Test
    fun injectionAdvanceNotice() {
        val data = Data(settings = Settings(
            method = Method(type = MethodType.INJECTION, start = start, trackedFrom = start, injectionWeeks = 12),
            notifications = Notifications(daysBefore = 7, periodDaysBefore = 0),
        ))
        val list = alerts(data, at("2026-01-06", "00:00"), at("2026-04-01", "00:00"))
        assertEquals(listOf(at("2026-03-23", "09:00"), at("2026-03-30", "21:00")), list.map { it.at })
    }

    @Test
    fun periodNoticeAndDailyLog() {
        val days = (0..4).associate { LocalDate.parse("2026-01-01").plusDays(it.toLong()) to DayLog(Flow.MEDIUM) } +
            (0..4).associate { LocalDate.parse("2026-01-29").plusDays(it.toLong()) to DayLog(Flow.MEDIUM) } +
            (LocalDate.parse("2026-02-10") to DayLog(note = "fine"))
        val data = Data(days = days, settings = Settings(notifications = Notifications(periodDaysBefore = 2, dailyLog = true)))
        val list = alerts(data, at("2026-02-09", "12:00"), at("2026-02-25", "00:00"))
        val bleed = list.filterIsInstance<BleedSoon>().single()
        assertEquals(at("2026-02-24", "09:00"), bleed.at)
        val logs = list.filterIsInstance<LogReminder>().map { it.day.dayOfMonth }
        assertTrue(9 in logs && 10 !in logs && 11 in logs)
    }
}
