package ch.lauzhack.energyguard.companion

import ch.lauzhack.energyguard.app.BreakSource
import ch.lauzhack.energyguard.app.TestClock
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.DashboardState
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Companion JSON with the app-model fields stays backward compatible both ways (docs/API.md "Phone companion"). */
class CompanionCompatTest {
    /** Exactly the fields an old phone (before M1) knows. */
    @Serializable
    private data class OldPhoneState(
        val score: Int, val level: Level, val reasons: List<String>, val calibrated: Boolean,
        val coach: CompanionCoach? = null, val ts: Long, val llmCalls: Int = 0,
        val calibrationMs: Long = 60_000, val windowMs: Long = 60_000, val runId: Int = 0,
    )

    private fun newPcJson(): String {
        val clock = TestClock()
        val store = clock.store()
        store.onFatigue(FatigueState(62, Level.TIRED, listOf("long blinks +80%"), true), clock.now)
        store.setDashboard(DashboardState(fatigue = FatigueState(62, Level.TIRED, listOf("long blinks +80%"), true)))
        store.onGuardBlock()
        store.startBreak()
        store.addJournalEntry(kss = 7, stress = 4, caffeine = 3, note = "SECRET-NOTE")
        return CompanionJson.encodeToString(CompanionState.serializer(), store.state.value.toCompanionState(clock.now))
    }

    @Test fun oldPhoneReadsNewPc() {
        val old = CompanionJson.decodeFromString(OldPhoneState.serializer(), newPcJson())
        assertEquals(62, old.score)
        assertEquals(Level.TIRED, old.level)
    }

    @Test fun newPhoneReadsNewPc() {
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), newPcJson()).clampAppFields()
        val energy = assertNotNull(s.energy)
        assertEquals(true, energy.onBreak)
        assertEquals(BreakSource.USER, energy.breakSource)
        assertNotNull(s.recovery)
        assertEquals(1, s.stats?.phishingBlocked)
        assertEquals(1, s.stats?.breaksTaken)
        assertEquals(0, s.stats?.imagesSent)
    }

    @Test fun newPhoneReadsOldPc() {
        val oldJson = """{"score":40,"level":"OK","reasons":[],"calibrated":true,"coach":null,"ts":1,"llmCalls":0,
            "calibrationMs":15000,"windowMs":10000,"runId":0,"vision":null,"input":null}"""
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), oldJson).clampAppFields()
        assertNull(s.energy)
        assertNull(s.recovery)
        assertNull(s.stats)
        assertEquals(60, s.toDashboardState().fatigue.concentration, "old fields map exactly as before")
    }

    @Test fun journalNeverLeavesTheDevice() {
        val json = newPcJson()
        assertFalse("SECRET-NOTE" in json, json)
        assertFalse("journal" in json, json)
        assertFalse("kss" in json, json)
        assertFalse("stress" in json, json)
        assertFalse("caffeine" in json, json)
    }

    @Test fun phoneClampsUntrustedValues() {
        val evil = """{"score":40,"level":"OK","reasons":[],"calibrated":true,"ts":1,
            "energy":{"battery":999,"onBreak":true,"breakSource":"AUTO"},"recovery":-5,
            "stats":{"focusedMs":-1,"breaksTaken":-3,"phishingBlocked":2}}"""
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), evil).clampAppFields()
        assertEquals(100, s.energy?.battery)
        assertEquals(0, s.recovery)
        assertEquals(0, s.stats?.focusedMs)
        assertEquals(0, s.stats?.breaksTaken)
        assertEquals(2, s.stats?.phishingBlocked)
        assertEquals(Intervention.BREAK, CompanionCoach("x", Intervention.BREAK, false).kind)
    }

    @Test fun phoneGetsThePcLiveSignals() {
        val clock = TestClock()
        val store = clock.store()
        store.setDashboard(DashboardState(
            vision = VisionFrame(ts = 0, face = true, blinkCount = 22, perclos = 0.18, eyesClosed = false, source = "logiinsight-ir"),
            input = InputStats(30_000, 4200.0, 6, overshootRatio = 1.25, jitter = 2.0, keyCount = 55, backspaceRatio = 0.12, meanInterKeyMs = 268.0),
        ))
        val json = CompanionJson.encodeToString(CompanionState.serializer(), store.state.value.toCompanionState(clock.now))
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), json).clampAppFields()
        val signals = s.phoneSignals()
        assertEquals("Logitech webcam · IR", signals.source)
        assertEquals(22, signals.blinksPerMin)
        assertEquals(18, signals.perclosPct)
        assertEquals(80, signals.precisionPct)
        assertEquals(268, signals.msPerKey)
        assertEquals(12, signals.backspacePct)
        assertNotNull(s.energy?.sleepHours)
        assertFalse("SECRET" in json)
    }

    @Test fun phoneFallsBackToRawSignalsFromAnOldPc() {
        val oldJson = """{"score":40,"level":"OK","reasons":[],"calibrated":true,"ts":1,
            "vision":{"ts":1,"face":true,"blinkCount":9,"perclos":0.05,"source":"mediapipe"},
            "input":{"windowMs":30000,"mouseDistancePx":100.0,"clickCount":1,"overshootRatio":1.0,"jitter":1.4,
                     "keyCount":0,"backspaceRatio":0.0,"meanInterKeyMs":0.0}}"""
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), oldJson).clampAppFields()
        assertNull(s.signals)
        val signals = s.phoneSignals()
        assertEquals("MediaPipe", signals.source)
        assertEquals(9, signals.blinksPerMin)
        assertNull(signals.jitterX, "no baseline on the phone: raw jitter only")
        assertEquals(1.4, signals.jitterPerS)
        assertEquals(false, signals.typing)
    }
}
