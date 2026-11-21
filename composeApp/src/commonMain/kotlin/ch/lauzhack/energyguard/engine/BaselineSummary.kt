package ch.lauzhack.energyguard.engine

import kotlinx.serialization.Serializable

/** Personal baseline learned during calibration (FatigueEngine.baselineSummary(), contract in docs/API.md). */
@Serializable
data class BaselineSummary(
    val calibrated: Boolean = false,
    val blinkRatePerMin: Double? = null,  // blinks / 60 s
    val blinkMs: Double? = null,          // mean blink duration
    val perclos: Double? = null,          // 0..1
    val mouseJitter: Double? = null,      // direction reversals / s
    val interKeyMs: Double? = null,       // typing rhythm: mean gap between keys
    val backspaceRatio: Double? = null,   // typo rate
) {
    companion object {
        val Empty = BaselineSummary()
    }
}
