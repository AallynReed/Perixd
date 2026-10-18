package net.mildhope.perixd.domain

import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Symptom
import java.time.LocalDate

data class PastCycle(val start: LocalDate, val length: Int?, val periodLength: Int)

/** Logged cycles, newest first; the newest has no length yet because it hasn't ended. */
fun pastCycles(periods: List<Period>): List<PastCycle> =
    periods.mapIndexed { i, p ->
        PastCycle(p.start, periods.getOrNull(i + 1)?.let { daysBetween(p.start, it.start) }, p.length)
    }.reversed()

data class SymptomSummary(val symptom: Symptom, val days: Int, val averageIntensity: Double, val typicalDays: IntRange?)

/** How often each symptom was logged in the last [window] days, and the cycle days it usually falls on. */
fun symptomSummary(days: Map<LocalDate, DayLog>, periods: List<Period>, today: LocalDate, window: Int = 180): List<SymptomSummary> {
    val recent = days.filterKeys { it <= today && daysBetween(it, today) < window }
    return Symptom.entries.mapNotNull { symptom ->
        val logged = recent.filterValues { symptom in it.symptoms }
        if (logged.isEmpty()) return@mapNotNull null
        val cycleDays = logged.keys.mapNotNull { cycleDayOf(it, periods) }.sorted()
        SymptomSummary(
            symptom = symptom,
            days = logged.size,
            averageIntensity = logged.values.map { it.symptoms.getValue(symptom) }.average(),
            typicalDays = cycleDays.takeIf { it.isNotEmpty() }?.let { it[(it.size - 1) / 4]..it[(it.size - 1) * 3 / 4] },
        )
    }.sortedByDescending { it.days }
}

/** Average flow level (1 spotting to 4 heavy) on each day of the last [recent] periods. */
fun flowByDay(days: Map<LocalDate, DayLog>, periods: List<Period>, recent: Int = 6, maxDays: Int = 8): List<Double?> {
    val last = periods.takeLast(recent)
    return (0 until maxDays).map { offset ->
        last.mapNotNull { days[it.start.plusDays(offset.toLong())]?.flow?.let { f -> f.ordinal + 1.0 } }
            .takeIf { it.isNotEmpty() }?.average()
    }.dropLastWhile { it == null }
}

data class Adherence(val due: Int, val done: Int)

/** Doses marked done among those due in the last [window] days, today included once its reminder time has passed or it's done. */
fun adherence(data: Data, today: LocalDate, window: Int = 30): Adherence {
    val method = data.settings.method
    val from = method.remindFrom() ?: return Adherence(0, 0)
    val due = dates(maxOf(from, today.minusDays(window - 1L)), today)
        .filter { method.doseOn(it) != null && (it < today || it in data.doses) }
        .toList()
    return Adherence(due.size, due.count { it in data.doses })
}
