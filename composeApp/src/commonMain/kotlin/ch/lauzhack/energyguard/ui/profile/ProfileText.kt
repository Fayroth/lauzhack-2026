package ch.lauzhack.energyguard.ui.profile

import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.SampleData

/** One day of the "Last 7 days" chart. [avg] = average concentration (null = no data that day). */
data class DayBar(val day: String, val weekday: String, val avg: Int?, val breaks: Int, val today: Boolean)

/** The 7 bars, oldest first, and whether they are [sample] data (demo, or fewer than 2 real days). */
data class WeekView(val bars: List<DayBar>, val sample: Boolean)

/** Real days with data needed before the chart shows them instead of sample days. */
const val MIN_REAL_DAYS = 2

/** Last 7 days ending today (pure, unit-tested): History + today's live numbers, or [SampleData.days]. */
fun lastSevenDays(state: AppState): WeekView {
    val today = state.stats.day
    val t = SampleData.epochDay(today) ?: return WeekView(emptyList(), sample = true)
    val byDay = state.history.associateBy { it.day }
    val real = (0 until 7).map { i ->
        val day = SampleData.dayKey(t - 6 + i)
        if (day == today) DayBar(day, SampleData.weekday(day), state.todayAvgConcentration, state.stats.breaksTaken, today = true)
        else byDay[day].let { DayBar(day, SampleData.weekday(day), it?.avgConcentration, it?.breaksTaken ?: 0, today = false) }
    }
    if (!state.demo && real.count { it.avg != null } >= MIN_REAL_DAYS) return WeekView(real, sample = false)
    val sample = SampleData.days(today).map {
        DayBar(it.day, SampleData.weekday(it.day), it.avgConcentration, it.breaksTaken, today = it.day == today)
    }
    return WeekView(sample, sample = true)
}
