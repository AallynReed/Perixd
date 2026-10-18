package net.mildhope.perixd

import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Settings
import net.mildhope.perixd.domain.Period
import net.mildhope.perixd.domain.cycleStats
import net.mildhope.perixd.domain.forecast
import net.mildhope.perixd.domain.periods
import net.mildhope.perixd.domain.withdrawalOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CyclesTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun bleeding(vararg starts: String, length: Int = 5, flow: Flow = Flow.MEDIUM) =
        starts.flatMap { s -> (0 until length).map { d(s).plusDays(it.toLong()) to DayLog(flow = flow) } }.toMap()

    @Test
    fun groupsConsecutiveDaysAndBridgesOneGap() {
        val days = mapOf(
            d("2026-01-01") to DayLog(Flow.HEAVY),
            d("2026-01-02") to DayLog(Flow.MEDIUM),
            d("2026-01-04") to DayLog(Flow.LIGHT),
            d("2026-01-08") to DayLog(Flow.LIGHT),
        )
        assertEquals(listOf(Period(d("2026-01-01"), d("2026-01-04")), Period(d("2026-01-08"), d("2026-01-08"))), periods(days))
    }

    @Test
    fun spottingNeverStartsAPeriod() {
        val days = mapOf(
            d("2026-01-01") to DayLog(Flow.SPOTTING),
            d("2026-01-02") to DayLog(Flow.SPOTTING),
            d("2026-01-03") to DayLog(Flow.LIGHT),
            d("2026-01-15") to DayLog(Flow.SPOTTING),
            d("2026-01-20") to DayLog(symptoms = mapOf()),
        )
        assertEquals(listOf(Period(d("2026-01-03"), d("2026-01-03"))), periods(days))
    }

    @Test
    fun statsAverageRecentCyclesAndSkipGapsFromMissedLogging() {
        val periods = periods(bleeding("2026-01-01", "2026-01-29", "2026-02-28", "2026-06-01", "2026-06-27"))
        val stats = cycleStats(periods, d("2026-07-10"))
        assertEquals(listOf(28, 30, 26), stats.cycles)
        assertEquals(28, stats.average)
        assertEquals(26, stats.shortest)
        assertEquals(30, stats.longest)
        assertEquals(5, stats.periodAverage)
    }

    @Test
    fun ongoingPeriodDoesNotShortenAverageLength() {
        val days = bleeding("2026-01-01", "2026-01-29") + bleeding("2026-02-26", length = 2)
        assertEquals(5, cycleStats(periods(days), d("2026-02-27")).periodAverage)
    }

    @Test
    fun regularCyclePredictsADayWithOvulationAndFertileWindow() {
        val f = forecast(bleeding("2026-01-01", "2026-01-29", "2026-02-26"), Settings(), d("2026-03-05"))
        assertEquals(d("2026-03-26"), f.nextStart)
        assertEquals(d("2026-03-26")..d("2026-03-26"), f.window)
        assertEquals(8, f.cycleDay)
        assertEquals(d("2026-03-12"), f.ovulation)
        assertEquals(d("2026-03-07")..d("2026-03-13"), f.fertile)
        assertEquals(0, f.lateBy)
        assertEquals(d("2026-03-26")..d("2026-03-30"), f.bleeds.first())
        assertFalse(f.followsMethod)
    }

    @Test
    fun irregularCyclePredictsAWindow() {
        val f = forecast(bleeding("2026-01-01", "2026-01-25", "2026-03-01", "2026-03-30"), Settings(), d("2026-04-02"))
        assertEquals(d("2026-04-28"), f.nextStart)
        assertEquals(d("2026-04-23")..d("2026-05-04"), f.window)
        assertEquals(d("2026-04-04")..d("2026-04-21"), f.fertile)
    }

    @Test
    fun usesDefaultsWithOnlyOnePeriodLogged() {
        val f = forecast(bleeding("2026-03-01"), Settings(cycleLength = 32, periodLength = 4), d("2026-03-10"))
        assertEquals(d("2026-04-02"), f.nextStart)
        assertEquals(d("2026-04-02")..d("2026-04-06"), f.bleeds.first())
    }

    @Test
    fun reportsLateness() {
        val f = forecast(bleeding("2026-01-01", "2026-01-29"), Settings(), d("2026-03-01"))
        assertEquals(3, f.lateBy)
    }

    @Test
    fun noPredictionWithoutLogs() {
        assertNull(forecast(emptyMap(), Settings(), d("2026-03-01")).nextStart)
    }

    @Test
    fun shortCycleKeepsOvulationInsideTheCycle() {
        val f = forecast(bleeding("2026-01-01", "2026-01-16", "2026-01-31"), Settings(), d("2026-02-02"))
        assertEquals(d("2026-02-15"), f.nextStart)
        assertTrue(f.fertile!!.start > d("2026-01-31"))
    }

    private val pill = Settings(method = Method(type = MethodType.PILL, start = d("2026-01-05")))

    @Test
    fun pillHidesFertilityAndPredictsTheBleedInTheBreak() {
        val f = forecast(emptyMap(), pill, d("2026-01-10"))
        assertTrue(f.followsMethod)
        assertNull(f.ovulation)
        assertNull(f.fertile)
        assertEquals(d("2026-01-28"), f.nextStart)
        assertEquals(d("2026-01-28")..d("2026-02-01"), f.bleeds.first())
        assertEquals(d("2026-02-25"), f.bleeds[1].start)
    }

    @Test
    fun withdrawalOffsetLearnsFromLoggedBleeds() {
        val days = bleeding("2026-01-27", "2026-02-24", "2026-03-25", length = 4)
        assertEquals(1, withdrawalOffset(periods(days), pill))
        assertEquals(d("2026-04-21"), forecast(days, pill, d("2026-04-01")).nextStart)
    }

    @Test
    fun continuousMethodsHaveNoBleedPrediction() {
        val settings = Settings(method = Method(type = MethodType.PILL, start = d("2026-01-05"), activePills = 28, breakDays = 0))
        val f = forecast(bleeding("2026-01-01"), settings, d("2026-01-10"))
        assertNull(f.nextStart)
        assertNull(f.fertile)
        assertEquals(d("2026-01-01"), f.lastStart)
    }

    @Test
    fun copperIudKeepsFertilityEstimates() {
        val settings = Settings(method = Method(type = MethodType.LONG_ACTING, hormonal = false))
        val f = forecast(bleeding("2026-01-01", "2026-01-29"), settings, d("2026-02-02"))
        assertEquals(d("2026-02-26"), f.nextStart)
        assertEquals(d("2026-02-12"), f.ovulation)
    }
}
