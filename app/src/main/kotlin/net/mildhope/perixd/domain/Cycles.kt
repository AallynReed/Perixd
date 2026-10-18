package net.mildhope.perixd.domain

import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.Settings
import java.time.LocalDate
import kotlin.math.roundToInt

data class Period(val start: LocalDate, val end: LocalDate) {
    val length get() = daysBetween(start, end) + 1
}

/** Bleeding days grouped into periods; spotting never starts or extends one, and one unlogged day inside a period is bridged. */
fun periods(days: Map<LocalDate, DayLog>): List<Period> {
    val out = mutableListOf<Period>()
    for (day in days.filterValues { it.flow != null && it.flow != Flow.SPOTTING }.keys.sorted()) {
        val last = out.lastOrNull()
        if (last != null && daysBetween(last.end, day) <= 2) out[out.lastIndex] = last.copy(end = day)
        else out += Period(day, day)
    }
    return out
}

val CYCLE_RANGE = 15..90

data class CycleStats(
    val cycles: List<Int>,
    val average: Int?,
    val shortest: Int?,
    val longest: Int?,
    val periodAverage: Int?,
) {
    val variation get() = if (shortest != null && longest != null) longest - shortest else null
}

/**
 * Statistics over the most recent cycles. Gaps outside [CYCLE_RANGE] are treated as missed logging and left out,
 * and a period that may still be going on doesn't count towards the average length.
 */
fun cycleStats(periods: List<Period>, today: LocalDate, recent: Int = 6): CycleStats {
    val cycles = periods.zipWithNext { a, b -> daysBetween(a.start, b.start) }.filter { it in CYCLE_RANGE }.takeLast(recent)
    val finished = periods.filter { daysBetween(it.end, today) > 1 }.takeLast(recent)
    return CycleStats(
        cycles = cycles,
        average = cycles.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        shortest = cycles.minOrNull(),
        longest = cycles.maxOrNull(),
        periodAverage = finished.takeIf { it.isNotEmpty() }?.map { it.length }?.average()?.roundToInt(),
    )
}

data class Forecast(
    val lastStart: LocalDate? = null,
    val cycleDay: Int? = null,
    val cycleLength: Int? = null,
    /** The likeliest next start, and the earliest to latest window around it. */
    val nextStart: LocalDate? = null,
    val window: ClosedRange<LocalDate>? = null,
    val lateBy: Int = 0,
    val ovulation: LocalDate? = null,
    val fertile: ClosedRange<LocalDate>? = null,
    /** True when the bleed prediction follows the method's hormone-free days instead of cycle history. */
    val followsMethod: Boolean = false,
    val bleeds: List<ClosedRange<LocalDate>> = emptyList(),
    val fertileWindows: List<ClosedRange<LocalDate>> = emptyList(),
    val ovulations: List<LocalDate> = emptyList(),
)

/** A cycle whose shortest and longest recent lengths differ by more than this is predicted as a window, not a day. */
const val WINDOW_SPREAD = 3
const val DEFAULT_WITHDRAWAL_OFFSET = 2

fun forecast(days: Map<LocalDate, DayLog>, settings: Settings, today: LocalDate): Forecast {
    val periods = periods(days)
    val stats = cycleStats(periods, today)
    val periodLength = stats.periodAverage ?: settings.periodLength
    val lastStart = periods.lastOrNull()?.start
    val method = settings.method
    if (method.isHormonal) return methodForecast(periods, settings, today, periodLength, lastStart)
    lastStart ?: return Forecast()

    val average = stats.average ?: settings.cycleLength
    val spread = if ((stats.variation ?: 0) > WINDOW_SPREAD) (stats.shortest!! - average)..(stats.longest!! - average) else 0..0
    val luteal = settings.lutealLength
    val cycles = (1..4).map { k ->
        val begin = lastStart.plusDays((k - 1L) * average)
        val start = lastStart.plusDays(k.toLong() * average)
        val window = start.plusDays(spread.first.toLong())..start.plusDays(spread.last.toLong())
        val ovulation = maxOf(start.minusDays(luteal.toLong()), begin.plusDays(1))
        val fertile = maxOf(window.start.minusDays(luteal + 5L), begin.plusDays(1))..
            maxOf(window.endInclusive.minusDays(luteal - 1L), begin.plusDays(1))
        Cycle(start, window, ovulation, fertile)
    }
    val next = cycles.first()
    return Forecast(
        lastStart = lastStart,
        cycleDay = daysBetween(lastStart, today) + 1,
        cycleLength = average,
        nextStart = next.start,
        window = next.window,
        lateBy = daysBetween(next.window.endInclusive, today).coerceAtLeast(0),
        ovulation = next.ovulation,
        fertile = next.fertile,
        bleeds = cycles.map { it.start..it.start.plusDays(periodLength - 1L) }.filter { it.endInclusive >= today }.take(3),
        fertileWindows = cycles.map { it.fertile }.take(3),
        ovulations = cycles.map { it.ovulation }.take(3),
    )
}

private data class Cycle(val start: LocalDate, val window: ClosedRange<LocalDate>, val ovulation: LocalDate, val fertile: ClosedRange<LocalDate>)

private fun methodForecast(periods: List<Period>, settings: Settings, today: LocalDate, periodLength: Int, lastStart: LocalDate?): Forecast {
    val method = settings.method
    val regimen = method.regimen()
    val free = regimen?.hormoneFree
    val start = method.start
    if (regimen == null || free == null || start == null) return Forecast(lastStart = lastStart)
    val offset = withdrawalOffset(periods, settings)
    val length = minOf(periodLength, free.last - free.first + 1 - offset + 2).coerceAtLeast(1)
    val current = maxOf(method.cycleStart(today)!!, start)
    val bleeds = (-1..4).map { k ->
        val begin = current.plusDays(k.toLong() * regimen.length + free.first + offset)
        begin..begin.plusDays(length - 1L)
    }.filter { it.start >= start && it.endInclusive >= today }.take(3)
    val next = bleeds.firstOrNull()?.start
    return Forecast(
        lastStart = lastStart,
        nextStart = next,
        window = next?.let { it..it },
        followsMethod = true,
        bleeds = bleeds,
    )
}

/** How many days into the hormone-free days bleeding usually starts, from the last few logged withdrawal bleeds. */
fun withdrawalOffset(periods: List<Period>, settings: Settings): Int {
    val method = settings.method
    val free = method.regimen()?.hormoneFree ?: return DEFAULT_WITHDRAWAL_OFFSET
    val start = method.start ?: return DEFAULT_WITHDRAWAL_OFFSET
    val offsets = periods.filter { it.start >= start }
        .mapNotNull { p -> method.dayIndex(p.start)!!.takeIf { it in free }?.minus(free.first) }
        .takeLast(3)
    if (offsets.isEmpty()) return DEFAULT_WITHDRAWAL_OFFSET.coerceAtMost(free.last - free.first)
    return offsets.sorted()[offsets.size / 2]
}

/** Day of the cycle [date] falls on, counted from the latest period start on or before it. */
fun cycleDayOf(date: LocalDate, periods: List<Period>): Int? =
    periods.lastOrNull { it.start <= date }?.let { daysBetween(it.start, date) + 1 }
