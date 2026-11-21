package ch.lauzhack.energyguard.ui

import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlin.test.Test
import kotlin.test.assertEquals

class BreakAlertLogicTest {
    private fun logic() = BreakAlertLogic(cardAfterMs = 10_000, fullAfterMs = 10_000, snoozeMs = 30_000, awakeGraceMs = 5_000)
    private val tired = FatigueState(score = 60, level = Level.TIRED, reasons = listOf("eyes closing +347%"), calibrated = true)

    @Test
    fun nothingWhileFreshOrOk() {
        val l = logic()
        assertEquals(AlertStage.NONE, l.stage(Level.FRESH, false, 0))
        assertEquals(AlertStage.NONE, l.stage(Level.OK, false, 1_000))
    }

    @Test
    fun cardAsSoonAsTired() {
        assertEquals(AlertStage.CARD, logic().stage(Level.TIRED, false, 0))
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
        assertEquals(AlertStage.CARD, l.stage(Level.TIRED, true, 1_000))
        assertEquals(AlertStage.FULL, l.stage(Level.TIRED, true, 6_000))
    }

    @Test
    fun dismissLastsUntilTheNextTiredEpisode() {
        val l = logic()
        l.stage(Level.TIRED, false, 0)
        l.dismiss()
        assertEquals(AlertStage.NONE, l.stage(Level.EXHAUSTED, false, 20_000))
        assertEquals(AlertStage.NONE, l.stage(Level.OK, false, 21_000))
        assertEquals(AlertStage.CARD, l.stage(Level.TIRED, false, 22_000))
    }

    @Test
    fun snoozeHidesThenComesBackAsHintThenCard() {
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
        l.snooze(0)
        assertEquals(AlertStage.HINT, l.stage(Level.TIRED, false, 31_000))
        l.openCard(31_000)
        assertEquals(AlertStage.CARD, l.stage(Level.TIRED, false, 31_000))
    }

    @Test
    fun titleFollowsTheCoachKind() {
        assertEquals("Eyes off the screen, 5 min", breakTitle(null))
        assertEquals("Eyes off the screen, 5 min", breakTitle(CoachMessage("x", fromLlm = true, kind = Intervention.BREAK)))
        assertEquals("Stand up and walk a bit", breakTitle(CoachMessage("x", fromLlm = false, kind = Intervention.STAND_UP)))
        assertEquals("Eyes off the screen, 5 min", breakTitle(CoachMessage("x", fromLlm = false, kind = Intervention.RECOVERED)))
    }

    @Test
    fun detailIsTheCoachTextElseTheFallback() {
        assertEquals("Walk 5 min, then the hard email.", breakDetail(tired, CoachMessage("Walk 5 min, then the hard email.", true, Intervention.BREAK)))
        assertEquals("Take a 5-min break. Eyes closing ×4.5 vs usual.", breakDetail(tired, null))
        assertEquals("Take a 5-min break. Eyes closing ×4.5 vs usual.", breakDetail(tired, CoachMessage("Welcome back", false, Intervention.RECOVERED)))
        assertEquals("Take a 5-min break.", breakDetail(tired.copy(reasons = emptyList()), null))
    }

    @Test
    fun coachPercentagesBecomeFactors() {
        val msg = CoachMessage("Energy 28% at 18:40, eyes closing +347%: take a 5-minute break away from the screen.", false, Intervention.BREAK)
        assertEquals("Energy 28% at 18:40, eyes closing ×4.5 vs usual: take a 5-minute break away from the screen.", breakDetail(tired, msg))
        assertEquals("typing ×0.7 vs usual", factorize("typing -30%"))
    }

    @Test
    fun microSleepIsReadFromTheEngineReason() {
        val f = tired.copy(reasons = listOf("eyes closed 2.3 s", "long blinks +80%"))
        assertEquals("eyes closed 2.3 s", microSleepReason(f))
    }

    @Test
    fun windowLabelMatchesTheEngineWindow() {
        assertEquals("last minute", windowLabel(60_000))
        assertEquals("last 10 s", windowLabel(10_000))
        assertEquals("last 2 min", windowLabel(120_000))
    }
}
