package ch.lauzhack.energyguard.companion

import ch.lauzhack.energyguard.app.BreakSource
import ch.lauzhack.energyguard.app.TestClock
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
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
        store.addJournalEntry(kss = 7, note = "SECRET-NOTE")
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
        assertEquals(60, s.toDashboardState().fatigue.energy, "old fields map exactly as before")
    }

    @Test fun journalNeverLeavesTheDevice() {
        val json = newPcJson()
        assertFalse("SECRET-NOTE" in json, json)
        assertFalse("journal" in json, json)
        assertFalse("kss" in json, json)
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
}
