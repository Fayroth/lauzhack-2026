package ch.lauzhack.energyguard.app

/**
 * Made-up, clearly labelled data for the demo (M5): the UI shows a "Sample data" pill next to anything built from it.
 * Never saved, never sent. Pure Kotlin: dates are computed from "YYYY-MM-DD" strings, no platform clock.
 */
object SampleData {
    private const val HOUR_MS = 3_600_000L

    // Self-rated sleepiness vs our concentration: correlated but noisy, r ≈ 0.66 (a real person, not a straight line).
    private val KSS = listOf(7, 6, 3, 4, 8, 2, 5, 6, 3, 7, 4, 5)
    private val CONCENTRATION = listOf(50, 46, 70, 52, 60, 79, 72, 58, 62, 48, 74, 57)

    /** 12 check-ins, newest first, one every 3 h back from [now]. */
    fun journal(now: Long): List<JournalEntry> = KSS.indices.map { i ->
        val ts = now - 20 * 60_000L - i * 3 * HOUR_MS
        JournalEntry(
            id = ts, ts = ts,
            kss = KSS[i],
            stress = if (i % 3 == 0) null else (KSS[i] + 1) / 2,
            caffeine = if (i % 2 == 0) i % 4 else null,
            note = if (i == 0) "Long meeting after lunch, eyes heavy." else null,
            concentration = CONCENTRATION[i],
        )
    }

    private val AVG = listOf(71, 64, 58, 75, 69, 52, 66)
    private val BREAKS = listOf(4, 3, 2, 5, 4, 1, 2)

    /** 7 days ending [today] ("YYYY-MM-DD"), oldest first. Empty if [today] is not a valid day. */
    fun days(today: String): List<DaySummary> {
        val t = epochDay(today) ?: return emptyList()
        return (0 until 7).map { i ->
            DaySummary(day = dayKey(t - 6 + i), avgConcentration = AVG[i], breaksTaken = BREAKS[i], checkIns = 2)
        }
    }

    /** Days since 1970-01-01 of a "YYYY-MM-DD" string (proleptic Gregorian), null if malformed. */
    fun epochDay(day: String): Long? {
        if (!Settings.DAY.matches(day)) return null
        val (y0, m, d) = day.split("-").map { it.toLong() }
        if (m !in 1..12 || d !in 1..31) return null
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** Inverse of [epochDay]. */
    fun dayKey(epochDay: Long): String {
        val z = epochDay + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "Mon".."Sun" of a "YYYY-MM-DD" day, "" if malformed. 1970-01-01 was a Thursday. */
    fun weekday(day: String): String = epochDay(day)?.let { WEEKDAYS[(((it + 3) % 7 + 7) % 7).toInt()] } ?: ""
}
