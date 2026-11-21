package ch.lauzhack.energyguard.engine

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * The brain: fuses vision + input signals into a 0-100 fatigue score.
 *
 * PLACEHOLDER (owner: Ali). Current logic:
 *  - during the first [calibrationMs], averages each signal to build a personal baseline;
 *  - afterwards, score = weighted sum of how much each signal got worse vs baseline.
 * TODO: rolling windows, smoothing (EMA), better weights, tests on recorded data.
 */
class FatigueEngine(
    private val calibrationMs: Long = 60_000,
) {
    private val _state = MutableStateFlow(FatigueState.Initial)
    val state: StateFlow<FatigueState> = _state.asStateFlow()

    private val clock = TimeSource.Monotonic
    private var start: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastVision: VisionFrame? = null
    private var lastInput: InputStats? = null

    // Baseline accumulators (sum, count) per signal.
    private val baseline = mutableMapOf<String, Pair<Double, Int>>()

    fun onVision(frame: VisionFrame) {
        lastVision = frame
        recompute()
    }

    fun onInput(stats: InputStats) {
        lastInput = stats
        recompute()
    }

    private fun signals(): Map<String, Double> = buildMap {
        lastVision?.takeIf { it.face }?.let { v ->
            v.perclos?.let { put("perclos", it) }
            v.lastBlinkMs?.let { put("blinkMs", it.toDouble()) }
        }
        lastInput?.let { i ->
            put("jitter", i.jitter)
            put("overshoot", i.overshootRatio)
            put("backspace", i.backspaceRatio)
        }
    }

    private fun recompute() {
        val startMark = start ?: clock.markNow().also { start = it }
        val current = signals()
        val calibrated = startMark.elapsedNow().inWholeMilliseconds >= calibrationMs

        if (!calibrated) {
            current.forEach { (k, v) ->
                val (s, n) = baseline[k] ?: (0.0 to 0)
                baseline[k] = (s + v) to (n + 1)
            }
            _state.value = FatigueState.Initial
            return
        }

        val reasons = mutableListOf<String>()
        var score = 0.0
        for ((key, value) in current) {
            val (sum, n) = baseline[key] ?: continue
            val base = (sum / n).coerceAtLeast(1e-6)
            val increase = ((value - base) / base).coerceAtLeast(0.0) // 0.5 = 50 % worse than baseline
            val w = WEIGHTS[key] ?: 0.0
            score += w * increase.coerceAtMost(2.0) / 2.0 * 100
            if (increase > 0.3) reasons += "${LABELS[key]} +${(increase * 100).roundToInt()}%"
        }
        val s = score.roundToInt().coerceIn(0, 100)
        _state.value = FatigueState(s, levelFor(s), reasons, calibrated = true)
    }

    companion object {
        private val WEIGHTS = mapOf(
            "perclos" to 0.35, "blinkMs" to 0.25,
            "jitter" to 0.15, "overshoot" to 0.15, "backspace" to 0.10,
        )
        private val LABELS = mapOf(
            "perclos" to "eyes closing", "blinkMs" to "long blinks",
            "jitter" to "mouse jitter", "overshoot" to "mouse overshoot", "backspace" to "typos",
        )

        fun levelFor(score: Int): Level = when {
            score < 25 -> Level.FRESH
            score < 50 -> Level.OK
            score < 75 -> Level.TIRED
            else -> Level.EXHAUSTED
        }
    }
}
