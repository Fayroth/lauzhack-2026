package ch.lauzhack.energyguard.engine

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic simulator: vision frames at 10 Hz, input stats at 1 Hz, timestamps driven by the test.
 * [tired] in 0..1 degrades every signal the way the mock sources do.
 */
private class Sim(seed: Int = 42) {
    val engine = FatigueEngine()
    private val rnd = Random(seed)
    var now = 1_800_000_000_000L
    var vision = true
    var input = true
    var face = true
    var eyesClosed = false
    var tired = 0.0
    private var nextInputTs = now

    val state: FatigueState get() = engine.state.value

    private fun n(a: Double) = (rnd.nextDouble() * 2 - 1) * a

    private fun step() {
        now += 100
        if (vision) {
            val t = tired
            engine.onVision(
                if (!face) VisionFrame(ts = now, face = false)
                else VisionFrame(
                    ts = now,
                    face = true,
                    ear = 0.30 - 0.10 * t + n(0.01),
                    eyesClosed = eyesClosed,
                    blinkCount = (14 + 10 * t + n(2.0)).toInt(),
                    lastBlinkMs = (160 + 300 * t + n(20.0)).toInt(),
                    perclos = 0.05 + 0.30 * t + n(0.01),
                    source = "mock",
                )
            )
        }
        if (input && now >= nextInputTs) {
            val t = tired
            engine.onInput(
                InputStats(
                    windowMs = 30_000,
                    mouseDistancePx = 8_000 + n(1_000.0),
                    clickCount = 20,
                    overshootRatio = 1.15 + 0.4 * t + n(0.03),
                    jitter = 1.0 + 1.5 * t + n(0.1),
                    keyCount = (120 - 40 * t).toInt(),
                    backspaceRatio = 0.05 + 0.10 * t + n(0.01),
                    meanInterKeyMs = 180 + 120 * t + n(10.0),
                ),
                ts = now,
            )
            nextInputTs += 1000
        }
    }

    /** Advance [ms] of simulated time, calling [each] with the state after every step. */
    fun run(ms: Long, each: (FatigueState) -> Unit = {}) {
        repeat((ms / 100).toInt()) { step(); each(state) }
    }

    /** Linearly ramp tiredness from its current value to [to] over [ms]. */
    fun ramp(to: Double, ms: Long, each: (FatigueState) -> Unit = {}) {
        val from = tired
        val steps = (ms / 100).toInt()
        repeat(steps) { i ->
            tired = from + (to - from) * (i + 1) / steps
            step(); each(state)
        }
    }

    fun calibrate() {
        run(61_000)
        assertTrue(state.calibrated, "should be calibrated after 60 s with a face")
    }
}

class FatigueEngineTest {

    @Test
    fun freshStaysFresh() {
        val sim = Sim()
        sim.calibrate()
        sim.run(10 * 60_000L) { s ->
            assertEquals(Level.FRESH, s.level, "fresh user flagged at score ${s.score}, reasons ${s.reasons}")
            assertTrue(s.score < 25)
        }
    }

    @Test
    fun gradualFatigueReachesTired() {
        val sim = Sim()
        sim.calibrate()
        sim.run(30_000) { assertEquals(Level.FRESH, it.level) }
        sim.ramp(to = 1.0, ms = 5 * 60_000L)
        sim.run(30_000)
        val s = sim.state
        assertTrue(s.level >= Level.TIRED, "expected TIRED+, got ${s.level} (score ${s.score})")
        assertTrue(s.reasons.isNotEmpty() && s.reasons.size <= 3, "reasons: ${s.reasons}")
        assertTrue(s.reasons.any { it.contains("+") && it.endsWith("%") }, "reasons should be plain words with a %: ${s.reasons}")
    }

    @Test
    fun microSleepIsInstantTired() {
        val sim = Sim()
        sim.calibrate()
        sim.run(30_000)
        assertEquals(Level.FRESH, sim.state.level)

        sim.eyesClosed = true
        sim.run(900)
        assertEquals(Level.FRESH, sim.state.level, "eyes closed < 1 s is just a blink")
        sim.run(300)
        assertEquals(Level.TIRED, sim.state.level, "eyes closed > 1 s must be instant TIRED")
        assertTrue(sim.state.reasons.first().startsWith("eyes closed"), "reasons: ${sim.state.reasons}")

        // Eyes open again: we don't flip straight back to FRESH.
        sim.eyesClosed = false
        sim.run(1_000)
        assertTrue(sim.state.level >= Level.TIRED)
    }

    @Test
    fun calibrationOnlyCountsTimeWithAFace() {
        val sim = Sim().apply { input = false }
        sim.run(30_000)
        sim.face = false
        sim.run(60_000)
        sim.face = true
        sim.run(20_000)
        assertFalse(sim.state.calibrated, "only 50 s with a face so far")
        sim.run(15_000)
        assertTrue(sim.state.calibrated)
    }

    @Test
    fun noFacePeriodsAreIgnored() {
        val sim = Sim()
        sim.calibrate()
        sim.face = false
        sim.run(2 * 60_000L) { s ->
            assertEquals(Level.FRESH, s.level, "no face must not look like fatigue (score ${s.score}, ${s.reasons})")
        }
        sim.face = true
        sim.run(60_000) { assertEquals(Level.FRESH, it.level) }
    }

    @Test
    fun hysteresisPreventsFlicker() {
        var level = Level.FRESH
        for (score in listOf(24, 26, 28, 24, 29, 26)) {
            level = FatigueEngine.nextLevel(level, score)
            assertEquals(Level.FRESH, level, "score $score should not leave FRESH yet")
        }
        level = FatigueEngine.nextLevel(level, 30)
        assertEquals(Level.OK, level)
        for (score in listOf(24, 21, 26, 20)) {
            level = FatigueEngine.nextLevel(level, score)
            assertEquals(Level.OK, level, "score $score should not drop back to FRESH yet")
        }
        assertEquals(Level.FRESH, FatigueEngine.nextLevel(level, 19))
        assertEquals(Level.OK, FatigueEngine.nextLevel(Level.TIRED, 44))
        assertEquals(Level.TIRED, FatigueEngine.nextLevel(Level.TIRED, 46))
        assertEquals(Level.EXHAUSTED, FatigueEngine.nextLevel(Level.FRESH, 85))

        // End to end: a monotonic, noisy ramp only ever moves the level up.
        val sim = Sim()
        sim.calibrate()
        var last = sim.state.level
        var changes = 0
        sim.ramp(to = 1.0, ms = 5 * 60_000L) { s ->
            if (s.level != last) {
                assertTrue(s.level > last, "level went back down from $last to ${s.level} during a ramp")
                changes++
                last = s.level
            }
        }
        assertTrue(changes <= 3)
    }

    @Test
    fun visionOnlyStillWorks() {
        val sim = Sim().apply { input = false }
        sim.calibrate()
        sim.run(60_000) { assertEquals(Level.FRESH, it.level) }
        sim.ramp(to = 1.0, ms = 3 * 60_000L)
        sim.run(30_000)
        assertTrue(sim.state.level >= Level.TIRED, "vision only: ${sim.state}")
    }

    @Test
    fun inputOnlyStillWorks() {
        val sim = Sim().apply { vision = false }
        sim.calibrate() // no sidecar at all: calibrates on input time instead
        sim.run(60_000) { assertEquals(Level.FRESH, it.level) }
        sim.ramp(to = 1.0, ms = 3 * 60_000L)
        sim.run(30_000)
        assertTrue(sim.state.level >= Level.TIRED, "input only: ${sim.state}")
    }
}
