package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.model.SleepPrior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SleepKssTest {
    @Test
    fun shortNightRaisesTheScoreWithinLimits() {
        assertEquals(0, SleepPrior.penalty(7.0))
        assertEquals(10, SleepPrior.penalty(5.0))
        assertEquals(20, SleepPrior.penalty(3.0))
        assertEquals(-5, SleepPrior.penalty(9.0))
    }

    @Test
    fun hoursAreShownByHalfHour() {
        assertEquals("7 h", hoursLabel(7f))
        assertEquals("6.5 h", hoursLabel(6.5f))
        assertEquals("3 h", hoursLabel(3.02f))
    }

    @Test
    fun correlationNeedsThreeVariedSamples() {
        assertNull(correlation(listOf(KssSample(3, 20), KssSample(7, 70))))
        assertNull(correlation(listOf(KssSample(5, 20), KssSample(5, 40), KssSample(5, 60))))
        val r = correlation(listOf(KssSample(2, 15), KssSample(5, 45), KssSample(8, 80)))!!
        assertTrue(r > 0.99, "expected a strong positive match, got $r")
    }
}
