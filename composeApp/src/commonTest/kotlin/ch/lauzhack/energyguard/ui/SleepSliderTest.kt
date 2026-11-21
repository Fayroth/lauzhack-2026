package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.model.SleepPrior
import kotlin.test.Test
import kotlin.test.assertEquals

class SleepSliderTest {
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
}
