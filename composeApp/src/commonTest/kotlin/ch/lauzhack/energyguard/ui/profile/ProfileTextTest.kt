package ch.lauzhack.energyguard.ui.profile

import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.DayStats
import ch.lauzhack.energyguard.app.DaySummary
import ch.lauzhack.energyguard.app.SampleData
import ch.lauzhack.energyguard.ui.journal.comparisonText
import ch.lauzhack.energyguard.ui.journal.pearson
import ch.lauzhack.energyguard.ui.today.chartTicks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTextTest {
    private val today = "2026-11-22"

    @Test fun dateHelpersRoundTripAndWeekdays() {
        assertEquals(0L, SampleData.epochDay("1970-01-01"))
        assertEquals("Thu", SampleData.weekday("1970-01-01"))
        assertEquals("Sun", SampleData.weekday(today))
        assertEquals("Sun", SampleData.weekday("2024-03-03")) // after a leap day
        assertEquals("2026-03-01", SampleData.dayKey(SampleData.epochDay("2026-02-28")!! + 1))
        assertEquals("2024-02-29", SampleData.dayKey(SampleData.epochDay("2024-03-01")!! - 1))
        for (d in listOf("2000-02-29", "1999-12-31", today)) assertEquals(d, SampleData.dayKey(SampleData.epochDay(d)!!))
        assertNull(SampleData.epochDay("22.11.2026"))
        assertEquals("", SampleData.weekday("x"))
    }

    @Test fun sampleJournalHasARealisticCorrelation() {
        val entries = SampleData.journal(now = 1_000_000_000_000L)
        val r = pearson(entries.map { it.kss!!.toDouble() }, entries.map { (100 - it.concentration).toDouble() })!!
        assertTrue(r in 0.6..0.75, "r = $r")
        assertTrue(comparisonText(entries).startsWith("r = 0.6"), comparisonText(entries))
        assertEquals(entries.sortedByDescending { it.ts }, entries)
    }

    @Test fun demoOrFewRealDaysShowSampleDays() {
        val demo = lastSevenDays(AppState(stats = DayStats(today), demo = true, todayAvgConcentration = 60))
        assertTrue(demo.sample)
        assertEquals(7, demo.bars.size)
        assertTrue(demo.bars.last().today)
        assertEquals(today, demo.bars.last().day)
        val oneRealDay = lastSevenDays(AppState(stats = DayStats(today), todayAvgConcentration = 60))
        assertTrue(oneRealDay.sample)
    }

    @Test fun realDaysFillTheWeekWithGaps() {
        val history = listOf(DaySummary("2026-11-21", avgConcentration = 70, breaksTaken = 3), DaySummary("2026-11-18", avgConcentration = 55))
        val week = lastSevenDays(AppState(stats = DayStats(today, breaksTaken = 2), history = history, todayAvgConcentration = 64))
        assertFalse(week.sample)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), week.bars.map { it.weekday })
        assertEquals(listOf(null, null, 55, null, null, 70, 64), week.bars.map { it.avg })
        assertEquals(2, week.bars.last().breaks)
    }

    @Test fun chartTicksAreRoundHours() {
        // Full demo day 08:00 -> 20:30: 4-hour steps.
        assertEquals(listOf("08:00", "12:00", "16:00", "20:00"), chartTicks(8 * 60, 20 * 60 + 30).map { it.second })
        // 08:00 -> 12:55: 08:00 and 12:00.
        assertEquals(listOf("08:00", "12:00"), chartTicks(8 * 60, 12 * 60 + 55).map { it.second })
        // Started at 14:10, now 16:40: whole hours.
        assertEquals(listOf("15:00", "16:00"), chartTicks(14 * 60 + 10, 16 * 60 + 40).map { it.second })
        // 20 minutes of data: quarter hours.
        assertTrue(chartTicks(9 * 60 + 3, 9 * 60 + 23).all { it.second.endsWith(":15") || it.second.endsWith(":00") })
        assertEquals(emptyList(), chartTicks(600, 600))
        val ticks = chartTicks(8 * 60, 20 * 60 + 30)
        assertEquals(0f, ticks.first().first)
        assertTrue(ticks.all { it.first in 0f..1f })
    }
}
