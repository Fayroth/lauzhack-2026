package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.model.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakAlertLogicTest {
    @Test
    fun showsOnlyWhenTiredOrWorse() {
        val logic = BreakAlertLogic()
        assertFalse(logic.isVisible(Level.FRESH, 0))
        assertFalse(logic.isVisible(Level.OK, 0))
        assertTrue(logic.isVisible(Level.TIRED, 0))
        assertTrue(logic.isVisible(Level.EXHAUSTED, 0))
    }

    @Test
    fun dismissLastsUntilTheNextTiredEpisode() {
        val logic = BreakAlertLogic()
        assertTrue(logic.isVisible(Level.TIRED, 0))
        logic.dismiss()
        assertFalse(logic.isVisible(Level.TIRED, 1_000))
        assertFalse(logic.isVisible(Level.EXHAUSTED, 2_000)) // still the same episode
        assertFalse(logic.isVisible(Level.OK, 3_000))        // recovered
        assertTrue(logic.isVisible(Level.TIRED, 4_000))      // new episode: alert again
    }

    @Test
    fun snoozeHidesForTheSnoozeDuration() {
        val logic = BreakAlertLogic(snoozeMs = 10_000)
        logic.snooze(nowMs = 1_000)
        assertFalse(logic.isVisible(Level.TIRED, 5_000))
        assertTrue(logic.isVisible(Level.TIRED, 11_000))
    }

    @Test
    fun detailUsesTheTopReasonAsAFactor() {
        val f = ch.lauzhack.energyguard.model.FatigueState(score = 60, level = Level.TIRED, reasons = listOf("eyes closing +347%"), calibrated = true)
        assertEquals("Eyes closing ×4.5 vs usual. Look away, stretch, breathe.", breakDetail(f))
    }
}
