package net.mildhope.perixd.domain

import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Notifications
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class Dose { PILL, PLACEBO, PATCH_APPLY, PATCH_CHANGE, PATCH_REMOVE, RING_INSERT, RING_REMOVE, RING_CHANGE, INJECTION }

/** One repeating cycle of a method: which dose falls on each day, and which days are hormone-free. */
class Regimen(private val doses: List<Dose?>, val hormoneFree: IntRange?) {
    val length get() = doses.size
    fun dose(index: Int) = doses[index]
}

fun Method.regimen(): Regimen? = when (type) {
    MethodType.PILL -> {
        val doses = List(activePills.coerceIn(1, MAX_PILLS)) { Dose.PILL } +
            List(placeboPills.coerceIn(0, 7)) { Dose.PLACEBO } +
            List(breakDays.coerceIn(0, 7)) { null }
        val active = activePills.coerceIn(1, MAX_PILLS)
        Regimen(doses, if (doses.size > active) active until doses.size else null)
    }
    MethodType.PATCH ->
        if (hormoneFreeWeek) weekly(mapOf(0 to Dose.PATCH_APPLY, 7 to Dose.PATCH_CHANGE, 14 to Dose.PATCH_CHANGE, 21 to Dose.PATCH_REMOVE), 21..27)
        else weekly(mapOf(0 to Dose.PATCH_CHANGE, 7 to Dose.PATCH_CHANGE, 14 to Dose.PATCH_CHANGE, 21 to Dose.PATCH_CHANGE), null)
    MethodType.RING ->
        if (hormoneFreeWeek) weekly(mapOf(0 to Dose.RING_INSERT, 21 to Dose.RING_REMOVE), 21..27)
        else weekly(mapOf(0 to Dose.RING_CHANGE), null)
    MethodType.INJECTION -> Regimen(listOf(Dose.INJECTION) + List(injectionWeeks.coerceIn(4, 26) * 7 - 1) { null }, null)
    MethodType.NONE, MethodType.LONG_ACTING -> null
}

const val MAX_PILLS = 91

private fun weekly(doses: Map<Int, Dose>, hormoneFree: IntRange?) = Regimen(List(28) { doses[it] }, hormoneFree)

val Method.isHormonal get() = when (type) {
    MethodType.NONE -> false
    MethodType.LONG_ACTING -> hormonal
    else -> true
}

val Method.isPill get() = type == MethodType.PILL

fun Method.dayIndex(date: LocalDate): Int? {
    val length = regimen()?.length ?: return null
    return start?.let { daysBetween(it, date).mod(length) }
}

/** The first day of the method cycle that [date] falls in. */
fun Method.cycleStart(date: LocalDate): LocalDate? = dayIndex(date)?.let { date.minusDays(it.toLong()) }

fun Method.doseOn(date: LocalDate): Dose? {
    val start = start ?: return null
    if (date < start) return null
    return regimen()?.dose(dayIndex(date)!!)
}

fun Method.remindFrom(): LocalDate? = start?.let { maxOf(it, trackedFrom ?: it) }

/** Which number this dose is among doses of the same kind in its cycle, e.g. pill 12 of 21. */
fun Method.doseNumber(date: LocalDate): Pair<Int, Int>? {
    val regimen = regimen() ?: return null
    val index = dayIndex(date) ?: return null
    val dose = regimen.dose(index) ?: return null
    val same = (0 until regimen.length).filter { regimen.dose(it) == dose || isPatchChange(regimen.dose(it), dose) }
    return same.indexOf(index) + 1 to same.size
}

private fun isPatchChange(a: Dose?, b: Dose) = a in PATCH_ON && b in PATCH_ON

private val PATCH_ON = setOf(Dose.PATCH_APPLY, Dose.PATCH_CHANGE)

fun Notifications.timeOn(date: LocalDate) =
    if (weekendTime != null && (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)) weekendTime else time

fun Notifications.doseMoment(date: LocalDate, zone: ZoneId): ZonedDateTime = date.atTime(timeOn(date)).atZone(zone)

fun daysBetween(from: LocalDate, to: LocalDate) = ChronoUnit.DAYS.between(from, to).toInt()

/** IN_PACK until its reminder time, then GHOST (it should be out but isn't marked) until it's marked TAKEN. */
enum class PillState { IN_PACK, GHOST, TAKEN }

fun pillState(method: Method, doses: Map<LocalDate, Long>, notifications: Notifications, date: LocalDate, now: ZonedDateTime) = when {
    date in doses || date < (method.trackedFrom ?: method.start ?: date) -> PillState.TAKEN
    !notifications.doseMoment(date, now.zone).isAfter(now) -> PillState.GHOST
    else -> PillState.IN_PACK
}
