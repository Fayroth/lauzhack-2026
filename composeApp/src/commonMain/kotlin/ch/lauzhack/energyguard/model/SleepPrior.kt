package ch.lauzhack.energyguard.model

import kotlin.math.roundToInt

/**
 * "Sleep last night" prior (#15): a short night makes you start the day more tired.
 * Written by the dashboard slider, read by FatigueEngine on every frame (contract: docs/API.md).
 */
object SleepPrior {
    const val NORMAL_HOURS = 7.0

    var hours: Double = NORMAL_HOURS

    /** Fatigue points added to the score: 5 per hour under 7 h, capped at +20; a long night gives at most -5. */
    fun penalty(h: Double = hours): Int = ((NORMAL_HOURS - h) * 5).roundToInt().coerceIn(-5, 20)
}
