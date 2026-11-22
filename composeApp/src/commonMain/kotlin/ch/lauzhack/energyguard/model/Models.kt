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
    /** Perception path: "logiinsight-rgb", "logiinsight-ir", "mediapipe", "mock" (older stubs: "webcam"). */
    val source: String = "unknown",

    // --- Optional, Logi Gaze Tracker (may be absent) ---
    val gazeOnScreen: Boolean? = null,
    val gazeX: Double? = null,          // 0..1, 0 = left
    val gazeY: Double? = null,          // 0..1, 0 = top
    val offScreenRatio: Double? = null, // fraction of the last 60 s with gaze off screen

    // --- Optional, Logi Hands Tracker (may be absent) ---
    val handNearEyes: Boolean? = null,  // eye rubbing
    val headOnHand: Boolean? = null,
)

/** Mouse + keyboard statistics over a rolling window. Counts/timings only, NEVER key contents. Serializable for the phone companion (#48). */
@Serializable
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
    /** What the gauge shows ("Concentration"): 100 = fresh. Not the energy battery (AppStore), which is slow over the day. */
    val concentration: Int get() = 100 - score

    companion object {
        val Initial = FatigueState(score = 0, level = Level.FRESH, reasons = emptyList(), calibrated = false)
    }
}
