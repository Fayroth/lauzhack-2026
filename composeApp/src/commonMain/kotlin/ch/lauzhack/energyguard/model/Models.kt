package ch.lauzhack.energyguard.model

import kotlinx.serialization.Serializable

/**
 * One message from the vision sidecar (ws://localhost:8765, ~10 Hz).
 * Mirrors docs/API.md: update the doc FIRST if you change this.
 * When [face] is false, the other fields may be absent (null).
 */
@Serializable
data class VisionFrame(
    val type: String = "vision",
    val ts: Long,
    val face: Boolean,
    val ear: Double? = null,
    val eyesClosed: Boolean? = null,
    val blinkCount: Int? = null,
    val lastBlinkMs: Int? = null,
    val perclos: Double? = null,
    val yawn: Boolean? = null,
    val headPitch: Double? = null,
    val headYaw: Double? = null,
    val source: String = "webcam",
)

/** Mouse + keyboard statistics over a rolling window. Counts/timings only, NEVER key contents. */
data class InputStats(
    val windowMs: Long,          // e.g. 30_000
    val mouseDistancePx: Double,
    val clickCount: Int,
    val overshootRatio: Double,  // path length / straight distance near clicks
    val jitter: Double,          // high-frequency micro-movement while hovering
    val keyCount: Int,           // counts only, never key contents
    val backspaceRatio: Double,
    val meanInterKeyMs: Double,
)

enum class Level { FRESH, OK, TIRED, EXHAUSTED }

data class FatigueState(
    val score: Int,              // 0 = fresh, 100 = exhausted
    val level: Level,
    val reasons: List<String>,   // e.g. ["long blinks", "mouse jitter +40%"]
    val calibrated: Boolean,
) {
    /** What the gauge shows: 100 % energy = fresh. */
    val energy: Int get() = 100 - score

    companion object {
        val Initial = FatigueState(score = 0, level = Level.FRESH, reasons = emptyList(), calibrated = false)
    }
}
