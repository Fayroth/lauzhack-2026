package ch.lauzhack.energyguard.demo

import ch.lauzhack.energyguard.model.Level
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoModeTest {

    /** Replays the demo script through the real engine (vision 10 Hz, input 1 Hz, same as the mock sources). */
    @Test
    fun demoScriptReachesTiredAndExhaustedOnTime() {
        val rnd = Random(42)
        val engine = DemoScript.newEngine()
        val t0 = 1_800_000_000_000L
        var firstTired: Long? = null
        var firstExhausted: Long? = null
        var levelAt20s: Level? = null
        for (ms in 0L..120_000L step 100) {
            val tired = DemoScript.tiredness(ms)
            engine.onVision(mockVisionFrame(t0 + ms, tired, rnd))
            if (ms % 1_000 == 0L) engine.onInput(mockInputStats(tired, rnd), t0 + ms)
            val s = engine.state.value
            if (ms == 20_000L) { levelAt20s = s.level; assertTrue(s.calibrated, "calibrated by 20 s") }
            if (firstTired == null && s.level >= Level.TIRED) firstTired = ms
            if (firstExhausted == null && s.level == Level.EXHAUSTED) firstExhausted = ms
        }
        println("demo: TIRED at ${firstTired} ms, EXHAUSTED at ${firstExhausted} ms")
        assertEquals(Level.FRESH, levelAt20s)
        assertNotNull(firstTired); assertNotNull(firstExhausted)
        assertTrue(firstTired in 50_000L..65_000L, "TIRED at $firstTired ms, expected ~60 s")
        assertTrue(firstExhausted in 80_000L..95_000L, "EXHAUSTED at $firstExhausted ms, expected ~90 s")
        assertTrue(engine.state.value.reasons.isNotEmpty(), "reasons are shown")
    }
}
