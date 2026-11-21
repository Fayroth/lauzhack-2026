package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.engine.FatigueEngine
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame

/** Everything the dashboard needs to render one frame. */
data class DashboardState(
    val fatigue: FatigueState = FatigueState.Initial,
    val vision: VisionFrame? = null,
    val input: InputStats? = null,
    val coach: CoachMessage? = null,   // last EnergyCoach intervention (break alert #8): text + kind + fromLlm
    val llmCallCount: Int = 0,         // real LLM calls so far (frugality counter #14)
    val calibrationMs: Long = FatigueEngine.DEFAULT_CALIBRATION_MS, // engine's calibration length: countdown source
    val windowMs: Long = FatigueEngine.DEFAULT_WINDOW_MS,           // engine's rolling window: "over the last ..." label
    val runId: Int = 0,                // changes when the engine restarts (F5 demo replay): key per-run UI state on it
)
