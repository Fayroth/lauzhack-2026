package ch.lauzhack.energyguard.app

import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level

/**
 * Energy battery (0-100, slow over the day). Pure and deterministic; constants are the contract in docs/API.md.
 * Starts from last night's sleep, drains with time awake (faster when TIRED/EXHAUSTED), refills during breaks.
 */
object EnergyBattery {
    const val FULL_SLEEP_HOURS = 7.0      // >= 7 h -> starts at 100
    const val MIN_SLEEP_HOURS = 3.0       // <= 3 h -> starts at 60
    const val START_PER_SLEEP_HOUR = 10.0 // 5 h -> 80
    const val DRAIN_PER_HOUR = 5.0        // FRESH / OK
    const val TIRED_FACTOR = 2.0          // 10 %/h
    const val EXHAUSTED_FACTOR = 3.0      // 15 %/h
    const val REFILL_PER_HOUR = 30.0      // during a break
    const val MAX_STEP_MS = 60_000L       // longest integration step (frozen / suspended app never jumps)
    const val IDLE_BREAK_MS = 120_000L    // no face + no input for this long = automatic break
    const val ACTIVE_MOUSE_PX = 50.0
    const val DEMO_TIME_SCALE = 120.0     // demo: 1 real s = 2 min of battery time

    private const val HOUR_MS = 3_600_000.0

    fun startFromSleep(hours: Double): Double {
        val h = if (hours.isFinite()) hours.coerceIn(MIN_SLEEP_HOURS, FULL_SLEEP_HOURS) else FULL_SLEEP_HOURS
        return 100.0 - START_PER_SLEEP_HOUR * (FULL_SLEEP_HOURS - h)
    }

    /** Signed rate in %/h: negative = drain, positive = refill. */
    fun ratePerHour(level: Level, onBreak: Boolean): Double = when {
        onBreak -> REFILL_PER_HOUR
        level == Level.EXHAUSTED -> -DRAIN_PER_HOUR * EXHAUSTED_FACTOR
        level == Level.TIRED -> -DRAIN_PER_HOUR * TIRED_FACTOR
        else -> -DRAIN_PER_HOUR
    }

    /** One step of [dtMs] battery-time (already scaled in demo mode). Always clamped to 0..100. */
    fun step(battery: Double, dtMs: Long, level: Level, onBreak: Boolean): Double =
        (battery + ratePerHour(level, onBreak) * dtMs.coerceAtLeast(0) / HOUR_MS).coerceIn(0.0, 100.0)

    /** Did the user touch the mouse/keyboard in this window? (Counts only, never contents.) */
    fun isActive(stats: InputStats): Boolean =
        stats.clickCount > 0 || stats.keyCount > 0 || stats.mouseDistancePx >= ACTIVE_MOUSE_PX
}
