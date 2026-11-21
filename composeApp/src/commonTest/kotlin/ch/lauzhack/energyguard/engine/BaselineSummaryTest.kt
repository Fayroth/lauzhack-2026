package ch.lauzhack.energyguard.engine

import ch.lauzhack.energyguard.demo.DemoScript
import ch.lauzhack.energyguard.demo.mockInputStats
import ch.lauzhack.energyguard.demo.mockVisionFrame
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineSummaryTest {
    @Test fun emptyUntilCalibratedThenPersonalBaseline() {
        val engine = DemoScript.newEngine()
        assertEquals(BaselineSummary.Empty, engine.baselineSummary())
        val rnd = Random(1)
        var ts = 1_000_000L
        repeat(100) { i -> // 10 s: still calibrating (15 s in demo)
            engine.onVision(mockVisionFrame(ts, 0.0, rnd)); if (i % 10 == 0) engine.onInput(mockInputStats(0.0, rnd), ts); ts += 100
        }
        val during = engine.baselineSummary()
        assertFalse(during.calibrated)
        assertNull(during.perclos)
        val before = engine.state.value
        repeat(100) { i ->
            engine.onVision(mockVisionFrame(ts, 0.0, rnd)); if (i % 10 == 0) engine.onInput(mockInputStats(0.0, rnd), ts); ts += 100
        }
        val b = engine.baselineSummary()
        assertTrue(b.calibrated)
        assertEquals(0.05, assertNotNull(b.perclos), 0.01)
        assertEquals(160.0, assertNotNull(b.blinkMs), 15.0)
        assertEquals(14.0, assertNotNull(b.blinkRatePerMin), 2.0)
        assertEquals(1.0, assertNotNull(b.mouseJitter), 0.1)
        assertEquals(180.0, assertNotNull(b.interKeyMs), 10.0)
        assertEquals(0.05, assertNotNull(b.backspaceRatio), 0.01)
        assertEquals(before.calibrated, false, "reading the baseline does not change the engine")
    }
}
