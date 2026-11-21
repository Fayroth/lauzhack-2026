package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.model.SleepPrior
import kotlin.test.Test
import kotlin.test.assertEquals

class SleepPriorTest {
    @Test
    fun shortNightRaisesTheScoreWithinLimits() {
        assertEquals(0, SleepPrior.penalty(7.0))
        assertEquals(10, SleepPrior.penalty(5.0))
        assertEquals(20, SleepPrior.penalty(3.0))
        assertEquals(-5, SleepPrior.penalty(9.0))
    }
}
