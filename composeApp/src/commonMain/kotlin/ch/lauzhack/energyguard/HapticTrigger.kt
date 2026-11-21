package ch.lauzhack.energyguard

import ch.lauzhack.energyguard.haptics.HapticPattern
import ch.lauzhack.energyguard.model.Level

/**
 * Decides when the mouse vibrates (issue #2): GENTLE when entering TIRED, WARNING when entering EXHAUSTED,
 * only on the way up, at most once per [cooldownMs]. Exception: escalating from a GENTLE to a WARNING
 * is never swallowed by the cooldown (otherwise "exhausted" could go unnoticed).
 */
class HapticTrigger(private val cooldownMs: Long = 120_000) {
    private var level = Level.FRESH
    private var lastPulseTs: Long? = null
    private var lastPattern: HapticPattern? = null

    /** Feed every level update; returns the pattern to play now, or null. */
    fun onLevel(newLevel: Level, nowMs: Long): HapticPattern? {
        val previous = level
        level = newLevel
        if (newLevel <= previous) return null
        val pattern = when (newLevel) {
            Level.TIRED -> HapticPattern.GENTLE
            Level.EXHAUSTED -> HapticPattern.WARNING
            else -> return null
        }
        val inCooldown = lastPulseTs?.let { nowMs - it < cooldownMs } ?: false
        val escalation = pattern == HapticPattern.WARNING && lastPattern == HapticPattern.GENTLE
        if (inCooldown && !escalation) return null
        lastPulseTs = nowMs
        lastPattern = pattern
        return pattern
    }
}
