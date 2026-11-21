package ch.lauzhack.energyguard.input

import ch.lauzhack.energyguard.model.InputStats
import kotlin.math.hypot

sealed interface InputEvent {
    val t: Long
}

data class MouseMove(override val t: Long, val x: Int, val y: Int) : InputEvent

data class MouseClick(override val t: Long, val x: Int, val y: Int) : InputEvent

data class KeyPress(override val t: Long, val isCorrection: Boolean) : InputEvent

/**
 * Pure rolling-window aggregation of raw input events into [InputStats] (no hooks, no threads: unit-testable).
 * Keyboard events carry a timestamp and a "correction key" flag only, never the key itself.
 */
class InputStatsCalculator(val windowMs: Long = 30_000) {

    private val moves = ArrayDeque<MouseMove>()
    private val clicks = ArrayDeque<MouseClick>()
    private val keys = ArrayDeque<KeyPress>()

    fun add(event: InputEvent) {
        when (event) {
            is MouseMove -> moves.addLast(event)
            is MouseClick -> clicks.addLast(event)
            is KeyPress -> keys.addLast(event)
        }
    }

    fun snapshot(nowMs: Long): InputStats {
        val cutoff = nowMs - windowMs
        while (moves.isNotEmpty() && moves.first().t < cutoff) moves.removeFirst()
        while (clicks.isNotEmpty() && clicks.first().t < cutoff) clicks.removeFirst()
        while (keys.isNotEmpty() && keys.first().t < cutoff) keys.removeFirst()
        return InputStats(
            windowMs = windowMs,
            mouseDistancePx = distance(),
            clickCount = clicks.size,
            overshootRatio = overshootRatio(),
            jitter = jitter(nowMs),
            keyCount = keys.size,
            backspaceRatio = backspaceRatio(),
            meanInterKeyMs = meanInterKeyMs(),
        )
    }

    private fun distance(): Double {
        var d = 0.0
        for (i in 1 until moves.size) d += dist(moves[i - 1].x, moves[i - 1].y, moves[i].x, moves[i].y)
        return d
    }

    /** Path length / straight distance over the [APPROACH_MS] before each click (1.0 = perfectly straight). */
    private fun overshootRatio(): Double {
        val ratios = mutableListOf<Double>()
        for (click in clicks) {
            val approach = moves.subList(firstMoveAtOrAfter(click.t - APPROACH_MS), firstMoveAtOrAfter(click.t + 1))
            if (approach.size < 3) continue
            var path = 0.0
            for (i in 1 until approach.size) {
                path += dist(approach[i - 1].x, approach[i - 1].y, approach[i].x, approach[i].y)
            }
            path += dist(approach.last().x, approach.last().y, click.x, click.y)
            val straight = dist(approach.first().x, approach.first().y, click.x, click.y)
            if (straight >= MIN_STRAIGHT_PX) ratios += (path / straight).coerceAtMost(MAX_RATIO)
        }
        return if (ratios.isEmpty()) 1.0 else ratios.average()
    }

    /**
     * Direction reversals per second between consecutive small, fast cursor steps (shaky hand),
     * over the last [JITTER_WINDOW_MS] only so that shaking shows up within one or two emissions.
     */
    private fun jitter(nowMs: Long): Double {
        val span = minOf(JITTER_WINDOW_MS, windowMs)
        val from = firstMoveAtOrAfter(nowMs - span)
        var reversals = 0
        var prevDx = 0
        var prevDy = 0
        var prevValid = false
        for (i in (from + 1) until moves.size) {
            val a = moves[i - 1]
            val b = moves[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val step = hypot(dx.toDouble(), dy.toDouble())
            if (step < JITTER_MIN_STEP_PX) continue
            val valid = step <= JITTER_MAX_STEP_PX && b.t - a.t <= JITTER_MAX_DT_MS
            if (valid && prevValid && dx * prevDx + dy * prevDy < 0) reversals++
            prevValid = valid
            prevDx = dx
            prevDy = dy
        }
        return reversals * 1000.0 / span
    }


    /** Index of the first move with t >= [t] (moves are time-ordered). */
    private fun firstMoveAtOrAfter(t: Long): Int {
        var lo = 0
        var hi = moves.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (moves[mid].t < t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun backspaceRatio(): Double =
        if (keys.isEmpty()) 0.0 else keys.count { it.isCorrection }.toDouble() / keys.size

    /** Mean time between key presses, ignoring pauses longer than [MAX_TYPING_GAP_MS]. */
    private fun meanInterKeyMs(): Double {
        val gaps = mutableListOf<Long>()
        for (i in 1 until keys.size) {
            val gap = keys[i].t - keys[i - 1].t
            if (gap in 1..MAX_TYPING_GAP_MS) gaps += gap
        }
        return if (gaps.isEmpty()) 0.0 else gaps.average()
    }

    private fun dist(x1: Int, y1: Int, x2: Int, y2: Int): Double =
        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())

    private companion object {
        const val APPROACH_MS = 800L
        const val MIN_STRAIGHT_PX = 40.0
        const val MAX_RATIO = 5.0
        const val JITTER_WINDOW_MS = 10_000L
        const val JITTER_MIN_STEP_PX = 1.0
        const val JITTER_MAX_STEP_PX = 80.0
        const val JITTER_MAX_DT_MS = 100L
        const val MAX_TYPING_GAP_MS = 2_000L
    }
}
