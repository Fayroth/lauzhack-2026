package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlin.test.Test
import kotlin.test.assertEquals

class BreakAlertLogicTest {
    private fun logic() = BreakAlertLogic(cardAfterMs = 10_000, fullAfterMs = 10_000, snoozeMs = 30_000, awakeGraceMs = 5_000)

    @Test
    fun nothingWhileFreshOrOk() {
        val l = logic()
        assertEquals(AlertStage.NONE, l.stage(Level.FRESH, false, 0))
        assertEquals(AlertStage.NONE, l.stage(Level.OK, false, 1_000))
    }

    @Test
    fun tiredStartsWithHintThenCardIfIgnored() {
        val l = logic()
        assertEquals(AlertStage.HINT, l.stage(Level.TIRED, false, 0))
        assertEquals(AlertStage.HINT, l.stage(Level.TIRED, false, 9_000))
        assertEquals(AlertStage.CARD, l.stage(Level.TIRED, false, 10_000))
    }

    @Test
    fun exhaustedGoesCardThenFullIfIgnored() {
        val l = logic()
        assertEquals(AlertStage.CARD, l.stage(Level.EXHAUSTED, false, 0))
        assertEquals(AlertStage.FULL, l.stage(Level.EXHAUSTED, false, 10_000))
    }

    @Test
    fun microSleepGoesFullAtOnceUnlessJustAcknowledged() {
        val l = logic()
        assertEquals(AlertStage.FULL, l.stage(Level.TIRED, true, 0))
        l.awake(0)
        assertEquals(AlertStage.HINT, l.stage(Level.TIRED, true, 1_000))
        assertEquals(AlertStage.FULL, l.stage(Level.TIRED, true, 6_000))
    }

    @Test
    fun dismissLastsUntilTheNextTiredEpisode() {
        val l = logic()
        l.stage(Level.TIRED, false, 0)
        l.dismiss()
        assertEquals(AlertStage.NONE, l.stage(Level.EXHAUSTED, false, 20_000))
        assertEquals(AlertStage.NONE, l.stage(Level.OK, false, 21_000))
        assertEquals(AlertStage.HINT, l.stage(Level.TIRED, false, 22_000))
    }

    @Test
    fun snoozeHidesThenRestartsFromHint() {
        val l = logic()
        l.stage(Level.TIRED, false, 0)
        l.snooze(15_000)
        assertEquals(AlertStage.NONE, l.stage(Level.TIRED, false, 40_000))
        assertEquals(AlertStage.HINT, l.stage(Level.TIRED, false, 45_000))
        assertEquals(AlertStage.CARD, l.stage(Level.TIRED, false, 55_000))
    }

    @Test
    fun hintClickOpensTheCard() {
        val l = logic()
        l.stage(Level.TIRED, false, 0)
        l.openCard(2_000)
        assertEquals(AlertStage.CARD, l.stage(Level.TIRED, false, 2_000))
    }

    @Test
    fun detailUsesTheTopReasonAsAFactor() {
        val f = FatigueState(score = 60, level = Level.TIRED, reasons = listOf("eyes closing +347%"), calibrated = true)
        assertEquals("Eyes closing ×4.5 vs usual. Look away, stretch, breathe.", breakDetail(f))
    }

    @Test
    fun microSleepIsReadFromTheEngineReason() {
        val f = FatigueState(score = 60, level = Level.TIRED, reasons = listOf("eyes closed 2.3 s", "long blinks +80%"), calibrated = true)
        assertEquals("eyes closed 2.3 s", microSleepReason(f))
    }
}
