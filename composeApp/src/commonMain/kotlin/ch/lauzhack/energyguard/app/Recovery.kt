package ch.lauzhack.energyguard.app

import kotlin.math.roundToInt

/**
 * Recovery score (0-100): sleep 50 % + breaks taken vs needed today 30 % + journal check-in 20 % (optional).
 * Weights are renormalized over the components present. Pure; contract in docs/API.md.
 */
object RecoveryScore {
    const val W_SLEEP = 0.5
    const val W_BREAKS = 0.3
    const val W_CHECK_IN = 0.2
    const val BREAK_EVERY_MS = 3_600_000L // one break needed per hour of focus
    const val SLEEP_ZERO_HOURS = 4.0      // 4 h -> 0
    const val SLEEP_FULL_HOURS = 8.0      // >= 8 h -> 100

    fun sleepComponent(hours: Double): Double {
        if (!hours.isFinite()) return 0.0
        return ((hours - SLEEP_ZERO_HOURS) / (SLEEP_FULL_HOURS - SLEEP_ZERO_HOURS)).coerceIn(0.0, 1.0) * 100
    }

    fun breaksNeeded(focusedMs: Long): Int = (focusedMs.coerceAtLeast(0) / BREAK_EVERY_MS).toInt()

    fun breaksComponent(taken: Int, needed: Int): Double =
        if (needed <= 0) 100.0 else (taken.toDouble() / needed).coerceIn(0.0, 1.0) * 100

    /** null when the entry has neither a KSS nor a stress value (then the weight is renormalized away). */
    fun checkInComponent(entry: JournalEntry?): Double? {
        if (entry == null) return null
        val parts = listOfNotNull(
            entry.kss?.takeIf { it in JournalEntry.KSS_RANGE }?.let { (9 - it) / 8.0 * 100 },
            entry.stress?.takeIf { it in JournalEntry.STRESS_RANGE }?.let { (5 - it) / 4.0 * 100 },
        )
        return if (parts.isEmpty()) null else parts.average()
    }

    fun compute(sleepHours: Double, breaksTaken: Int, breaksNeeded: Int, checkIn: JournalEntry?): Int {
        val parts = listOfNotNull(
            W_SLEEP to sleepComponent(sleepHours),
            W_BREAKS to breaksComponent(breaksTaken, breaksNeeded),
            checkInComponent(checkIn)?.let { W_CHECK_IN to it },
        )
        val total = parts.sumOf { it.first }
        return (parts.sumOf { it.first * it.second } / total).roundToInt().coerceIn(0, 100)
    }
}
