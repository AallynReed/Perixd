package net.mildhope.perixd.ui

import android.content.Context
import android.content.res.Resources
import android.icu.text.DateFormatSymbols
import android.text.format.DateFormat
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Symptom
import net.mildhope.perixd.domain.Dose
import net.mildhope.perixd.domain.dayIndex
import net.mildhope.perixd.domain.doseNumber
import net.mildhope.perixd.domain.regimen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

object Labels {
    fun locale(res: Resources) = res.configuration.locales[0]!!

    private fun format(res: Resources, skeleton: String, value: TemporalAccessor): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(res), skeleton), locale(res)).format(value)

    fun day(res: Resources, date: LocalDate) = format(res, "EEEMMMd", date)
    fun shortDay(res: Resources, date: LocalDate) = format(res, "MMMd", date)
    fun longDay(res: Resources, date: LocalDate) = format(res, "EEEEMMMMd", date)
    fun month(res: Resources, date: LocalDate) = format(res, "MMMMy", date)

    /** A weekday label narrow enough for a seven-column grid; Portuguese abbreviations are full words, so those fall back to the short form. */
    fun weekday(res: Resources, day: DayOfWeek): String {
        val symbols = DateFormatSymbols.getInstance(locale(res))
        val index = day.value % 7 + 1
        val abbreviated = symbols.getWeekdays(DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED)[index].trimEnd('.')
        return if (abbreviated.length <= 4) abbreviated
        else symbols.getWeekdays(DateFormatSymbols.STANDALONE, DateFormatSymbols.SHORT)[index].trimEnd('.')
    }

    fun time(context: Context, time: LocalTime): String =
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale(context.resources)).format(time)

    fun range(res: Resources, from: LocalDate, to: LocalDate) =
        if (from == to) shortDay(res, from) else res.getString(R.string.date_range, shortDay(res, from), shortDay(res, to))

    /** What to do for a dose, e.g. "Time for pill 12 of 21". */
    fun doseAction(res: Resources, method: Method, day: LocalDate, dose: Dose): String {
        val (n, of) = method.doseNumber(day) ?: (1 to 1)
        return when (dose) {
            Dose.PILL -> res.getString(R.string.dose_pill, n, of)
            Dose.PLACEBO -> res.getString(R.string.dose_placebo, n, of)
            Dose.PATCH_APPLY -> res.getString(R.string.dose_patch_apply)
            Dose.PATCH_CHANGE -> res.getString(R.string.dose_patch_change, n, of)
            Dose.PATCH_REMOVE -> res.getString(R.string.dose_patch_remove)
            Dose.RING_INSERT -> res.getString(R.string.dose_ring_insert)
            Dose.RING_REMOVE -> res.getString(R.string.dose_ring_remove)
            Dose.RING_CHANGE -> res.getString(R.string.dose_ring_change)
            Dose.INJECTION -> res.getString(R.string.dose_injection)
        }
    }

    fun doseHint(dose: Dose) = if (dose.isPill) R.string.dose_hint_pill else R.string.dose_hint_other

    fun doneAction(dose: Dose) = if (dose.isPill) R.string.taken else R.string.done

    fun doseNoun(dose: Dose) = when (dose) {
        Dose.PILL, Dose.PLACEBO -> R.string.noun_pill
        Dose.PATCH_APPLY -> R.string.noun_patch_apply
        Dose.PATCH_CHANGE -> R.string.noun_patch_change
        Dose.PATCH_REMOVE -> R.string.noun_patch_remove
        Dose.RING_INSERT -> R.string.noun_ring_insert
        Dose.RING_REMOVE -> R.string.noun_ring_remove
        Dose.RING_CHANGE -> R.string.noun_ring_change
        Dose.INJECTION -> R.string.noun_injection
    }

    /** Where [date] sits in the method's cycle, e.g. "Pill 12 of 21" or "Break day 3 of 7". */
    fun position(res: Resources, method: Method, date: LocalDate): String? {
        val regimen = method.regimen() ?: return null
        val index = method.dayIndex(date) ?: return null
        val free = regimen.hormoneFree
        return when (method.type) {
            MethodType.PILL -> when (regimen.dose(index)) {
                Dose.PILL -> res.getString(R.string.pos_pill, index + 1, method.activePills)
                Dose.PLACEBO -> res.getString(R.string.pos_placebo, index - method.activePills + 1, method.placeboPills)
                else -> res.getString(R.string.pos_break, index - method.activePills - method.placeboPills + 1, method.breakDays)
            }
            MethodType.PATCH -> if (free != null && index in free) res.getString(R.string.pos_patch_free, index - free.first + 1, free.count())
            else res.getString(R.string.pos_patch, index / 7 + 1)
            MethodType.RING -> if (free != null && index in free) res.getString(R.string.pos_ring_free, index - free.first + 1, free.count())
            else res.getString(R.string.pos_ring, index + 1, free?.first ?: regimen.length)
            else -> res.getString(R.string.pos_day, index + 1, regimen.length)
        }
    }

    fun methodName(type: MethodType) = when (type) {
        MethodType.NONE -> R.string.method_none
        MethodType.PILL -> R.string.method_pill
        MethodType.PATCH -> R.string.method_patch
        MethodType.RING -> R.string.method_ring
        MethodType.INJECTION -> R.string.method_injection
        MethodType.LONG_ACTING -> R.string.method_long_acting
    }

    fun methodHint(type: MethodType) = when (type) {
        MethodType.NONE -> R.string.method_none_hint
        MethodType.PILL -> R.string.method_pill_hint
        MethodType.PATCH -> R.string.method_patch_hint
        MethodType.RING -> R.string.method_ring_hint
        MethodType.INJECTION -> R.string.method_injection_hint
        MethodType.LONG_ACTING -> R.string.method_long_acting_hint
    }

    fun flowName(flow: Flow?) = when (flow) {
        null -> R.string.flow_none
        Flow.SPOTTING -> R.string.flow_spotting
        Flow.LIGHT -> R.string.flow_light
        Flow.MEDIUM -> R.string.flow_medium
        Flow.HEAVY -> R.string.flow_heavy
    }

    fun symptomName(symptom: Symptom) = when (symptom) {
        Symptom.CRAMPS -> R.string.symptom_cramps
        Symptom.HEADACHE -> R.string.symptom_headache
        Symptom.BACK_PAIN -> R.string.symptom_back_pain
        Symptom.BLOATING -> R.string.symptom_bloating
        Symptom.BREAST_TENDERNESS -> R.string.symptom_breast_tenderness
        Symptom.FATIGUE -> R.string.symptom_fatigue
        Symptom.NAUSEA -> R.string.symptom_nausea
        Symptom.ACNE -> R.string.symptom_acne
        Symptom.MOOD -> R.string.symptom_mood
    }

    fun intensityName(level: Int) = when (level) {
        1 -> R.string.intensity_mild
        2 -> R.string.intensity_moderate
        3 -> R.string.intensity_severe
        else -> R.string.intensity_none
    }
}

val Dose.isPill get() = this == Dose.PILL || this == Dose.PLACEBO
