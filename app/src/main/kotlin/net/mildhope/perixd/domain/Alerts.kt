package net.mildhope.perixd.domain

import net.mildhope.perixd.data.Data
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class DueDose(val day: LocalDate, val dose: Dose)

/** The latest scheduled dose whose reminder time has passed, if it hasn't been marked done. */
fun dueDose(data: Data, now: ZonedDateTime): DueDose? {
    val method = data.settings.method
    val regimen = method.regimen() ?: return null
    val from = method.remindFrom() ?: return null
    var day = now.toLocalDate()
    repeat(regimen.length + 1) {
        if (day < from) return null
        val dose = method.doseOn(day)
        if (dose != null && !data.settings.notifications.doseMoment(day, now.zone).isAfter(now)) {
            return if (day in data.doses) null else DueDose(day, dose)
        }
        day = day.minusDays(1)
    }
    return null
}

sealed interface Alert {
    val at: ZonedDateTime
}

data class DoseAlert(override val at: ZonedDateTime, val day: LocalDate, val kind: Kind) : Alert {
    enum class Kind { DUE, REPEAT, SNOOZE_END }
}

/** Advance notice of a dose, or of a new pill pack after a break. */
data class DoseSoon(override val at: ZonedDateTime, val day: LocalDate, val dose: Dose, val newPack: Boolean) : Alert

data class BleedSoon(override val at: ZonedDateTime, val start: LocalDate) : Alert

data class FertileSoon(override val at: ZonedDateTime, val start: LocalDate) : Alert

data class LogReminder(override val at: ZonedDateTime, val day: LocalDate) : Alert

/** State the reminder engine keeps between runs. */
data class Runtime(
    val lastCheck: Instant? = null,
    val snoozedUntil: Instant? = null,
    val snoozedDay: LocalDate? = null,
)

/** Every alert that falls after [from] and no later than [to], given the data as it is now. */
fun alerts(data: Data, from: ZonedDateTime, to: ZonedDateTime, runtime: Runtime = Runtime()): List<Alert> {
    val settings = data.settings
    val n = settings.notifications
    val method = settings.method
    val zone = from.zone
    val out = mutableListOf<Alert>()
    fun add(alert: Alert) {
        if (alert.at.isAfter(from) && !alert.at.isAfter(to)) out += alert
    }
    val firstDay = from.toLocalDate().minusDays(1)
    val lastDay = to.toLocalDate()
    val remindFrom = method.remindFrom()
    val regimen = method.regimen()

    if (regimen != null && remindFrom != null && n.doseEnabled) {
        for (day in dates(maxOf(firstDay, remindFrom), lastDay)) {
            if (method.doseOn(day) == null || day in data.doses) continue
            val due = n.doseMoment(day, zone)
            add(DoseAlert(due, day, DoseAlert.Kind.DUE))
            if (n.repeatMinutes > 0) {
                val next = nextDoseDay(method, day, regimen.length)?.let { n.doseMoment(it, zone) }
                val limit = minOf(next ?: due.plusDays(1), due.plusDays(1))
                val snoozeEnd = runtime.snoozedUntil?.takeIf { runtime.snoozedDay == day }
                var at = due.plusMinutes(n.repeatMinutes.toLong())
                while (at.isBefore(limit) && !at.isAfter(to)) {
                    if (snoozeEnd == null || !at.toInstant().isBefore(snoozeEnd)) add(DoseAlert(at, day, DoseAlert.Kind.REPEAT))
                    at = at.plusMinutes(n.repeatMinutes.toLong())
                }
            }
        }
        val snoozedDay = runtime.snoozedDay
        val snoozedUntil = runtime.snoozedUntil
        if (snoozedDay != null && snoozedUntil != null && snoozedDay !in data.doses) {
            add(DoseAlert(snoozedUntil.atZone(zone), snoozedDay, DoseAlert.Kind.SNOOZE_END))
        }
    }

    if (regimen != null && remindFrom != null) {
        val breakBeforePack = method.isPill && regimen.dose(regimen.length - 1) == null
        for (day in dates(maxOf(firstDay, remindFrom), lastDay.plusDays(15))) {
            val dose = method.doseOn(day) ?: continue
            if (day in data.doses) continue
            val notice = when {
                dose == Dose.PILL && breakBeforePack && method.dayIndex(day) == 0 && n.newPackNotice -> 1
                dose != Dose.PILL && dose != Dose.PLACEBO && n.daysBefore > 0 -> n.daysBefore
                else -> continue
            }
            val at = day.minusDays(notice.toLong()).atTime(n.noticeTime).atZone(zone)
            if (!at.isBefore(n.doseMoment(day, zone))) continue
            add(DoseSoon(at, day, dose, newPack = dose == Dose.PILL))
        }
    }

    if (n.periodDaysBefore > 0 || n.fertileNotice) {
        val forecast = forecast(data.days, settings, from.toLocalDate())
        if (n.periodDaysBefore > 0) {
            for (bleed in forecast.bleeds) {
                if (bleed.start <= from.toLocalDate()) continue
                add(BleedSoon(bleed.start.minusDays(n.periodDaysBefore.toLong()).atTime(n.noticeTime).atZone(zone), bleed.start))
            }
        }
        if (n.fertileNotice && !method.isHormonal) {
            for (window in forecast.fertileWindows) add(FertileSoon(window.start.atTime(n.noticeTime).atZone(zone), window.start))
        }
    }

    if (n.dailyLog) {
        for (day in dates(firstDay, lastDay)) {
            if (data.days[day]?.isEmpty == false) continue
            add(LogReminder(day.atTime(n.dailyLogTime).atZone(zone), day))
        }
    }
    return out.sortedBy { it.at }
}

/** When the engine next needs to wake up, if ever. */
fun nextWake(data: Data, now: ZonedDateTime, runtime: Runtime): ZonedDateTime? =
    (alerts(data, now, now.plusDays(2), runtime).firstOrNull() ?: alerts(data, now, now.plusDays(200), runtime).firstOrNull())?.at

enum class DoseAction { POST_ALERT, POST_SILENT, CANCEL, KEEP }

data class Plan(
    val dose: DueDose?,
    val doseAction: DoseAction,
    val notices: List<Alert>,
    val nextWake: ZonedDateTime?,
    val runtime: Runtime,
)

/** Decides what to show and when to wake next. Alerts missed by more than [catchUpHours] are dropped, not replayed. */
fun plan(data: Data, runtime: Runtime, now: ZonedDateTime, catchUpHours: Long = 12): Plan {
    val n = data.settings.notifications
    val earliest = now.minusHours(catchUpHours)
    val from = runtime.lastCheck?.atZone(now.zone)?.let { maxOf(it, earliest) } ?: now
    val due = if (n.doseEnabled) dueDose(data, now) else null
    val snoozeActive = runtime.snoozedUntil != null && now.toInstant().isBefore(runtime.snoozedUntil) && runtime.snoozedDay == due?.day
    val fired = alerts(data, from, now, runtime)
    val next = if (snoozeActive) runtime.copy(lastCheck = now.toInstant()) else Runtime(lastCheck = now.toInstant())
    val alerted = due != null && fired.any { it is DoseAlert && it.day == due.day }
    val action = when {
        due == null || snoozeActive -> DoseAction.CANCEL
        alerted -> DoseAction.POST_ALERT
        n.persistent -> DoseAction.POST_SILENT
        else -> DoseAction.KEEP
    }
    return Plan(due, action, fired.filter { it !is DoseAlert }, nextWake(data, now, next), next)
}

private fun nextDoseDay(method: net.mildhope.perixd.data.Method, after: LocalDate, length: Int): LocalDate? =
    (1..length).asSequence().map { after.plusDays(it.toLong()) }.firstOrNull { method.doseOn(it) != null }

fun dates(from: LocalDate, to: LocalDate): Sequence<LocalDate> =
    generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }
