package ch.lauzhack.energyguard.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputStatsCalculatorTest {

    @Test
    fun straightApproachGivesOvershootNearOne() {
        val calc = InputStatsCalculator(windowMs = 30_000)
        for (i in 0..20) calc.add(MouseMove(t = 1_000L + i * 10, x = i * 10, y = 0))
        calc.add(MouseClick(t = 1_210, x = 200, y = 0))
        val stats = calc.snapshot(nowMs = 2_000)
        assertEquals(1, stats.clickCount)
        assertEquals(200.0, stats.mouseDistancePx, 0.01)
        assertEquals(1.0, stats.overshootRatio, 0.01)
        assertEquals(0.0, stats.jitter, 1e-9)
    }

    @Test
    fun zigzagApproachRaisesOvershoot() {
        val calc = InputStatsCalculator()
        for (i in 0..20) calc.add(MouseMove(t = 1_000L + i * 10, x = i * 10, y = if (i % 2 == 0) 0 else 20))
        calc.add(MouseClick(t = 1_210, x = 200, y = 0))
        assertTrue(calc.snapshot(nowMs = 2_000).overshootRatio > 1.5)
    }

    @Test
    fun shakingInPlaceRaisesJitter() {
        val calc = InputStatsCalculator(windowMs = 10_000)
        for (i in 0..200) calc.add(MouseMove(t = 1_000L + i * 10, x = 500 + if (i % 2 == 0) 0 else 3, y = 300))
        assertTrue(calc.snapshot(nowMs = 4_000).jitter > 5.0)
    }

    @Test
    fun repeatedPositionsDoNotHideTremor() {
        val calc = InputStatsCalculator(windowMs = 10_000)
        val pattern = intArrayOf(500, 503, 503, 500)
        for (i in 0..200) calc.add(MouseMove(t = 1_000L + i * 10, x = pattern[i % 4], y = 300))
        assertTrue(calc.snapshot(nowMs = 4_000).jitter > 5.0)
    }

    @Test
    fun smoothMovementHasNoJitter() {
        val calc = InputStatsCalculator()
        for (i in 0..100) calc.add(MouseMove(t = 1_000L + i * 10, x = i * 5, y = i * 2))
        assertEquals(0.0, calc.snapshot(nowMs = 3_000).jitter, 1e-9)
    }

    @Test
    fun keyboardStatsUseTimingsOnly() {
        val calc = InputStatsCalculator()
        var t = 1_000L
        repeat(8) { calc.add(KeyPress(t = t, isCorrection = false)); t += 200 }
        repeat(2) { calc.add(KeyPress(t = t, isCorrection = true)); t += 200 }
        val stats = calc.snapshot(nowMs = t)
        assertEquals(10, stats.keyCount)
        assertEquals(0.2, stats.backspaceRatio, 1e-9)
        assertEquals(200.0, stats.meanInterKeyMs, 1e-9)
    }

    @Test
    fun pausesAreExcludedFromInterKeyTime() {
        val calc = InputStatsCalculator()
        calc.add(KeyPress(t = 1_000, isCorrection = false))
        calc.add(KeyPress(t = 1_100, isCorrection = false))
        calc.add(KeyPress(t = 10_000, isCorrection = false))
        calc.add(KeyPress(t = 10_300, isCorrection = false))
        assertEquals(200.0, calc.snapshot(nowMs = 11_000).meanInterKeyMs, 1e-9)
    }

    @Test
    fun oldEventsLeaveTheWindow() {
        val calc = InputStatsCalculator(windowMs = 30_000)
        calc.add(MouseClick(t = 1_000, x = 0, y = 0))
        calc.add(KeyPress(t = 1_000, isCorrection = true))
        val stats = calc.snapshot(nowMs = 40_000)
        assertEquals(0, stats.clickCount)
        assertEquals(0, stats.keyCount)
        assertEquals(1.0, stats.overshootRatio, 1e-9)
    }
}
