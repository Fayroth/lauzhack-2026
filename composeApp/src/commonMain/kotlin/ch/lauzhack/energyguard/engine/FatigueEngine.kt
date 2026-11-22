package ch.lauzhack.energyguard.engine

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.SleepPrior
import ch.lauzhack.energyguard.model.VisionFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The brain: fuses vision + input signals into a 0-100 fatigue score (owner: Ali).
 *
 * Pure Kotlin, no clock of its own: every sample carries its timestamp (epoch ms), so it is deterministic.
 *  1. Calibration: the first [calibrationMs] *with a face* (or of input time if no vision sidecar is running)
 *     builds a personal baseline (mean/std) per signal.
 *  2. Each signal is averaged over a rolling [windowMs] window and compared to its baseline as a z-score,
 *     clipped to [0, 3]. One-sided for every signal: only a change in the fatigue direction counts. For the blink rate that
 *     is MORE blinks (rate rises with sleepiness); fewer blinks mean focused reading/screen work, not fatigue.
 *  3. Weighted average of the z-scores, renormalized over the signals that are actually available
 *     (no face, no keyboard, no gaze tracker = those signals just drop out, no bias).
 *  4. Time-based EMA smoothing, then levels with hysteresis (±[MARGIN] around 25/50/75).
 *  5. Eyes closed > [MICRO_SLEEP_MS] (3 s, micro-sleep) → instant TIRED. Shorter closures (a glance at the keyboard,
 *     a slow blink) never force the level; they only count through PERCLOS. Euro NCAP tests micro-sleep detection with
 *     eyes closed up to 4 s. If the face is lost after the eyes have been closed >= [HEAD_DROP_MIN_CLOSED_MS] (head
 *     dropping), the closure keeps counting for up to [FACE_LOSS_HOLD_MS].
 *
 * Not thread-safe: call onVision/onInput from one thread (the Compose main dispatcher does that).
 */
class FatigueEngine(
    private val calibrationMs: Long = DEFAULT_CALIBRATION_MS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val emaTauMs: Double = 10_000.0,
) {
    private val _state = MutableStateFlow(FatigueState.Initial)
    val state: StateFlow<FatigueState> = _state.asStateFlow()

    private enum class Sig(val weight: Double, val minStd: Double) {
        PERCLOS(30.0, 0.03),
        BLINK_MS(20.0, 40.0),
        BLINK_RATE(10.0, 3.0),
        JITTER(15.0, 0.05),
        OVERSHOOT(10.0, 0.05),
        BACKSPACE(9.0, 0.02),    // keyboard = 15 in total
        INTER_KEY(6.0, 20.0),
        OFF_SCREEN(5.0, 0.05),   // Logi Gaze Tracker, only if present
        HAND_NEAR_EYES(5.0, 0.1), // Logi Hands Tracker, only if present
    }

    /** Rolling window + baseline (Welford) for one signal. */
    private class Track {
        val window = ArrayDeque<Pair<Long, Double>>()
        var windowSum = 0.0
        var n = 0
        var mean = 0.0
        var m2 = 0.0
        var firstTs = 0L
        var lastTs = 0L
        var frozen = false

        val std get() = if (n > 1) sqrt(m2 / (n - 1)) else 0.0
        val windowMean get() = windowSum / window.size

        fun add(ts: Long, v: Double) {
            window.addLast(ts to v); windowSum += v
            if (!frozen) {
                if (n == 0) firstTs = ts
                lastTs = ts
                n++
                val d = v - mean
                mean += d / n
                m2 += d * (v - mean)
            }
        }

        fun prune(oldest: Long) {
            while (window.isNotEmpty() && window.first().first < oldest) windowSum -= window.removeFirst().second
            if (window.isEmpty()) windowSum = 0.0
        }
    }

    private class Contribution(val sig: Sig, val z: Double, val current: Double, val baseline: Double)

    private val tracks = Sig.entries.associateWith { Track() }
    private var now = Long.MIN_VALUE
    private var calibrated = false
    private var calibElapsedMs = 0L
    private var lastFaceTs: Long? = null
    private var lastVisionTs: Long? = null
    private var lastInputTs: Long? = null
    private var eyesClosedSince: Long? = null
    private var lastClosedFaceTs: Long? = null // last frame WITH a face and the eyes closed
    private var ema = 0.0
    private var lastEmaTs: Long? = null
    private var level = Level.FRESH

    fun onVision(frame: VisionFrame) {
        val ts = frame.ts
        now = max(now, ts)
        lastVisionTs = ts
        if (frame.face) {
            lastFaceTs?.let { if (!calibrated) calibElapsedMs += (ts - it).coerceIn(0, MAX_VISION_GAP_MS) }
            lastFaceTs = ts
            frame.perclos?.let { add(Sig.PERCLOS, ts, it) }
            frame.lastBlinkMs?.let { add(Sig.BLINK_MS, ts, it.toDouble()) }
            frame.blinkCount?.let { add(Sig.BLINK_RATE, ts, it.toDouble()) }
            frame.offScreenRatio?.let { add(Sig.OFF_SCREEN, ts, it) }
            frame.handNearEyes?.let { add(Sig.HAND_NEAR_EYES, ts, if (it) 1.0 else 0.0) }
            if (frame.eyesClosed == true) {
                eyesClosedSince = eyesClosedSince ?: ts
                lastClosedFaceTs = ts
            } else {
                eyesClosedSince = null
            }
        } else {
            // No face: no calibration credit across the gap. A micro-sleep that was already under way (eyes closed >= 0.5 s,
            // then the head drops out of view) keeps counting for a short hold; otherwise no face = no micro-sleep.
            lastFaceTs = null
            val since = eyesClosedSince
            val lastClosed = lastClosedFaceTs
            val headDrop = since != null && lastClosed != null &&
                lastClosed - since >= HEAD_DROP_MIN_CLOSED_MS && ts - lastClosed <= FACE_LOSS_HOLD_MS
            if (!headDrop) eyesClosedSince = null
        }
        recompute()
    }

    /** [ts] = epoch ms when these stats were produced (InputStats has no timestamp of its own). */
    fun onInput(stats: InputStats, ts: Long) {
        now = max(now, ts)
        // No vision sidecar at all: calibrate on input time so a mouse/keyboard-only setup still works.
        val visionAbsent = lastVisionTs?.let { ts - it > VISION_ABSENT_MS } ?: true
        if (!calibrated && visionAbsent) {
            lastInputTs?.let { calibElapsedMs += (ts - it).coerceIn(0, MAX_INPUT_GAP_MS) }
        }
        lastInputTs = ts
        if (stats.mouseDistancePx >= MIN_MOUSE_PX) add(Sig.JITTER, ts, stats.jitter)
        if (stats.clickCount > 0) add(Sig.OVERSHOOT, ts, stats.overshootRatio)
        if (stats.keyCount >= MIN_KEYS) {
            add(Sig.BACKSPACE, ts, stats.backspaceRatio)
            add(Sig.INTER_KEY, ts, stats.meanInterKeyMs)
        }
        recompute()
    }

    /** Read-only view of the personal baseline (Profile tab). A value is null until that signal's baseline is frozen. */
    fun baselineSummary(): BaselineSummary {
        fun mean(sig: Sig) = tracks.getValue(sig).takeIf { it.frozen }?.mean
        return BaselineSummary(
            calibrated = calibrated,
            blinkRatePerMin = mean(Sig.BLINK_RATE),
            blinkMs = mean(Sig.BLINK_MS),
            perclos = mean(Sig.PERCLOS),
            mouseJitter = mean(Sig.JITTER),
            interKeyMs = mean(Sig.INTER_KEY),
            backspaceRatio = mean(Sig.BACKSPACE),
        )
    }

    private fun add(sig: Sig, ts: Long, v: Double) {
        if (v.isFinite()) tracks.getValue(sig).add(ts, v) // untrusted sidecar data: drop NaN/Infinity
    }

    private fun recompute() {
        tracks.values.forEach { it.prune(now - windowMs) }

        if (!calibrated && calibElapsedMs >= calibrationMs) {
            calibrated = true
            tracks.values.forEach { it.frozen = it.n >= MIN_BASELINE_N }
        }
        if (calibrated) {
            // Signals that showed up late (e.g. nobody typed during calibration) get their own shorter baseline.
            tracks.values.forEach {
                if (!it.frozen && it.n >= MIN_BASELINE_N && it.lastTs - it.firstTs >= calibrationMs / 2) it.frozen = true
            }
        }

        val contributions = if (!calibrated) emptyList() else tracks.mapNotNull { (sig, t) ->
            if (!t.frozen || t.window.isEmpty()) return@mapNotNull null
            val current = t.windowMean
            val std = maxOf(t.std, sig.minStd, 0.1 * abs(t.mean))
            val raw = (current - t.mean) / std
            Contribution(sig, raw.coerceIn(0.0, Z_MAX), current, t.mean)
        }
        val totalWeight = contributions.sumOf { it.sig.weight }
        val rawScore = if (totalWeight > 0) contributions.sumOf { it.sig.weight * it.z / Z_MAX } / totalWeight * 100 else 0.0

        val alpha = lastEmaTs?.let { 1 - exp(-(now - it).coerceAtLeast(0) / emaTauMs) } ?: 1.0
        ema += alpha * (rawScore - ema)
        lastEmaTs = now

        // Vision went silent (sidecar down) while eyes were closed: don't let input frames grow a fake micro-sleep.
        if (lastVisionTs.let { it == null || now - it > MAX_VISION_GAP_MS }) eyesClosedSince = null
        val closedMs = eyesClosedSince?.let { now - it } ?: 0L
        val microSleep = closedMs > MICRO_SLEEP_MS
        if (microSleep) ema = max(ema, MICRO_SLEEP_SCORE)

        // Sleep prior (#15): a short night shifts the whole day up (see SleepPrior / docs/API.md).
        val sleepPenalty = SleepPrior.penalty()
        val score = (ema.roundToInt() + sleepPenalty).coerceIn(0, 100)
        level = nextLevel(level, score)
        if (microSleep && level < Level.TIRED) level = Level.TIRED

        val reasons = buildList {
            if (microSleep) add("eyes closed ${tenths(closedMs)} s")
            if (sleepPenalty >= 5) add("short night (${tenths((SleepPrior.hours * 1000).roundToLong())} h)")
            contributions.filter { it.z >= REASON_MIN_Z }
                .sortedByDescending { it.sig.weight * it.z }
                .take(3 - size)
                .forEach { add(describe(it)) }
        }
        _state.value = FatigueState(score, level, reasons, calibrated)
    }

    private fun describe(c: Contribution): String {
        val pct = if (abs(c.baseline) < 1e-3) null else ((c.current - c.baseline) / abs(c.baseline) * 100).roundToInt()
        val delta = pct?.let { if (it >= 0) " +$it%" else " $it%" } ?: ""
        return when (c.sig) {
            Sig.PERCLOS -> "eyes closing$delta"
            Sig.BLINK_MS -> "long blinks$delta"
            Sig.BLINK_RATE -> "blink rate$delta"
            Sig.JITTER -> "shaky mouse$delta"
            Sig.OVERSHOOT -> "mouse overshoot$delta"
            Sig.BACKSPACE -> "more typos$delta"
            Sig.INTER_KEY -> "slower typing$delta"
            Sig.OFF_SCREEN -> "looking away ${(c.current * 100).roundToInt()}% of the time"
            Sig.HAND_NEAR_EYES -> "rubbing eyes"
        }
    }

    private fun tenths(ms: Long): String = (ms / 100).let { "${it / 10}.${it % 10}" }

    companion object {
        /** Real-mode defaults; also reported to the UI through DashboardState (demo mode uses shorter values). */
        const val DEFAULT_CALIBRATION_MS = 60_000L
        const val DEFAULT_WINDOW_MS = 60_000L
        const val MARGIN = 5
        private val BOUNDS = intArrayOf(25, 50, 75) // FRESH | OK | TIRED | EXHAUSTED
        private const val Z_MAX = 3.0
        private const val REASON_MIN_Z = 1.0
        private const val MICRO_SLEEP_MS = 3_000L // Euro NCAP micro-sleep test: eyes closed up to 4 s
        private const val MICRO_SLEEP_SCORE = 60.0
        private const val MAX_VISION_GAP_MS = 1_000L
        // Closed this long BEFORE the face is lost = head dropping. A blink or a ~1 s glance at the keyboard (the mesh is
        // often lost when looking down) is not.
        private const val HEAD_DROP_MIN_CLOSED_MS = 1_500L
        private const val FACE_LOSS_HOLD_MS = 3_000L
        private const val MAX_INPUT_GAP_MS = 6_000L // > JNativeHookInputSource emit period (5 s)
        private const val VISION_ABSENT_MS = 5_000L
        private const val MIN_BASELINE_N = 3
        private const val MIN_MOUSE_PX = 100.0
        private const val MIN_KEYS = 10

        /** Level with hysteresis: go up only at bound + MARGIN, go down only below bound - MARGIN. */
        fun nextLevel(current: Level, score: Int): Level {
            var i = current.ordinal
            while (i < BOUNDS.size && score >= BOUNDS[i] + MARGIN) i++
            if (i == current.ordinal) while (i > 0 && score < BOUNDS[i - 1] - MARGIN) i--
            return Level.entries[i]
        }
    }
}
