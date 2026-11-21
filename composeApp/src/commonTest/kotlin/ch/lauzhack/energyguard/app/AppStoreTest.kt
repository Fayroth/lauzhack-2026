package ch.lauzhack.energyguard.app

import ch.lauzhack.energyguard.coach.CoachCheckIn
import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStoreTest {
    private val clock = TestClock()
    private fun face(ts: Long) = VisionFrame(ts = ts, face = true)
    private fun noFace(ts: Long) = VisionFrame(ts = ts, face = false)

    @Test fun concentrationIsInverseScoreWithTop3Reasons() {
        val store = clock.store()
        store.onFatigue(FatigueState(62, Level.TIRED, listOf("a", "b", "c", "d"), calibrated = true), clock.now)
        val c = store.state.value.concentration
        assertEquals(38, c.value)
        assertEquals(Level.TIRED, c.level)
        assertEquals(listOf("a", "b", "c"), c.reasons)
        assertTrue(c.calibrated)
    }

    @Test fun focusTimeStreaksAndBreaks() {
        val store = clock.store()
        store.run(clock, 40 * MIN) { store.onVision(face(it)) }
        store.startBreak()
        store.run(clock, 5 * MIN) { store.onVision(face(it)) }
        store.endBreak()
        store.run(clock, 70 * MIN) { store.onVision(face(it)) }
        val s = store.state.value.stats
        assertEquals(110 * MIN, s.focusedMs)
        assertEquals(70 * MIN, s.longestFocusMs)
        assertEquals(70 * MIN, s.currentFocusMs)
        assertEquals(1, s.breaksTaken)
        assertEquals(1, s.breaksNeeded, "one per hour of focus")
    }

    @Test fun shortPausesDoNotEndAStreak() {
        val store = clock.store()
        store.run(clock, 30 * MIN) { store.onVision(face(it)) }
        store.run(clock, 90_000) { store.onVision(noFace(it)) } // 1.5 min away: not a break
        store.run(clock, 30 * MIN) { store.onVision(face(it)) }
        val s = store.state.value.stats
        assertEquals(0, s.breaksTaken)
        assertEquals(61 * MIN + 30_000, s.longestFocusMs)
    }

    @Test fun countersAndPrivacy() {
        val store = clock.store()
        repeat(25) { store.onVision(face(clock.now + it)) }
        store.onVision(noFace(clock.now + 30))
        store.onGuardBlock()
        store.onGuardBlock()
        store.onCoach(CoachMessage("Walk 5 min.", fromLlm = true, kind = Intervention.STAND_UP), llmCallCount = 1)
        store.tick(clock.now + 1_000)
        val s = store.state.value
        assertEquals(26, s.stats.framesProcessed)
        assertEquals(2, s.stats.phishingBlocked)
        assertEquals(1, s.stats.llmCalls)
        assertEquals(0, s.stats.imagesSent)
        assertEquals(0, s.stats.audioSent)
        assertEquals(0, s.stats.keysSent)
        val coach = assertNotNull(s.coach)
        assertEquals("AI", coach.tag)
        assertEquals(Intervention.STAND_UP, coach.kind)
        store.onCoach(CoachMessage("Break.", fromLlm = false), llmCallCount = 1)
        assertEquals("offline", store.state.value.coach?.tag)
    }

    @Test fun dayChangeArchivesTodayIntoHistory() {
        val persistence = InMemoryPersistence()
        val store = clock.store(persistence)
        store.onCoach(null, llmCallCount = 2)
        store.onGuardBlock()
        store.run(clock, 10 * MIN) { store.onVision(face(it)) }
        clock.now = DAY + 9 * HOUR // next morning, app still running
        store.onVision(face(clock.now))
        store.tick(clock.now)
        val s = store.state.value
        assertEquals("2026-11-22", s.stats.day)
        assertEquals(0, s.stats.llmCalls, "counter is per day")
        assertEquals(0, s.stats.phishingBlocked)
        val yesterday = s.history.single()
        assertEquals("2026-11-21", yesterday.day)
        assertEquals(2, yesterday.llmCalls)
        assertEquals(1, yesterday.phishingBlocked)
        assertEquals(10 * MIN, yesterday.focusedMs)
        assertEquals("2026-11-21", persistence.history.drop(1).first().day, "saved to disk too")
        store.onCoach(null, llmCallCount = 3)
        assertEquals(1, store.state.value.stats.llmCalls)
    }

    @Test fun historyKeepsSevenDays() {
        val store = clock.store()
        repeat(10) {
            clock.now += DAY
            store.tick(clock.now)
        }
        assertEquals(7, store.state.value.history.size)
        assertEquals("2026-11-30", store.state.value.history.first().day)
    }

    @Test fun journalEntriesAreValidated() {
        val store = clock.store()
        store.onFatigue(FatigueState(30, Level.OK, emptyList(), true), clock.now)
        val e = assertNotNull(store.addJournalEntry(kss = 4, stress = 9, caffeine = 2, note = "  slept badly  "))
        assertEquals(4, e.kss)
        assertNull(e.stress, "stress 9 is out of 1..5: dropped")
        assertEquals("slept badly", e.note)
        assertEquals(70, e.concentration, "our concentration at that moment")
        assertNull(store.addJournalEntry(kss = 0, note = "   "), "nothing valid -> rejected")
        val second = assertNotNull(store.addJournalEntry(caffeine = 3))
        assertTrue(second.id > e.id, "unique ids even at the same ms")
        assertEquals(listOf(second.id, e.id), store.state.value.journal.map { it.id }, "newest first")
        store.deleteJournalEntry(e.id)
        assertEquals(listOf(second.id), store.state.value.journal.map { it.id })
        assertEquals(1000, store.addJournalEntry(note = "x".repeat(5000))!!.note!!.length)
    }

    @Test fun checkInMovesRecovery() {
        val store = clock.store()
        store.setSleepHours(6.0) // sleep 67 (4-7 h), breaks 100 (none needed) -> 62.5 / 37.5 weights
        val before = store.state.value.recovery
        assertEquals(79, before)
        store.addJournalEntry(kss = 9, stress = 5) // check-in component 0 -> 50 % / 30 % / 20 %
        assertEquals(63, store.state.value.recovery)
        store.addJournalEntry(kss = 1) // the latest one counts: component 100
        assertEquals(83, store.state.value.recovery)
        store.addJournalEntry(caffeine = 2) // no KSS / stress: the previous check-in still counts
        assertEquals(83, store.state.value.recovery)
    }

    @Test fun concentrationOverrideAndCoachNumbers() {
        val store = clock.store()
        store.onFatigue(FatigueState(30, Level.OK, emptyList(), true), clock.now)
        assertNull(store.coachCheckIn(), "no check-in yet")
        assertEquals(41, store.addJournalEntry(kss = 6, concentration = 41)!!.concentration, "phone: the PC's value")
        assertEquals(100, store.addJournalEntry(stress = 2, concentration = 140)!!.concentration, "clamped")
        store.addJournalEntry(note = "only words")
        assertEquals(CoachCheckIn(kss = null, stress = 2, caffeine = null), store.coachCheckIn(), "latest with numbers, never the note")
        clock.now += DAY
        store.tick(clock.now)
        assertNull(store.coachCheckIn(), "yesterday's check-in is not today's")
    }

    @Test fun settingsAndBaseline() {
        val persistence = InMemoryPersistence()
        val store = clock.store(persistence)
        store.updateSettings { it.copy(hapticsOn = false, theme = ThemeMode.LIGHT, sleepHours = 42.0) }
        val s = store.state.value.settings
        assertEquals(false, s.hapticsOn)
        assertEquals(ThemeMode.LIGHT, s.theme)
        assertEquals(9.0, s.sleepHours, "clamped to 3..9")
        assertEquals("2026-11-21", s.sleepDay)
        assertEquals(s, persistence.settings, "saved on change")

        val b = BaselineSummary(calibrated = true, blinkRatePerMin = 15.0, perclos = 0.05)
        store.onFatigue(FatigueState.Initial, clock.now, b)
        assertEquals(b, store.state.value.profile.baseline)
    }

    @Test fun sleepHoursOnlyRestoredOnTheSameDay() {
        val today = InMemoryPersistence(settings = Settings(sleepHours = 5.0, sleepDay = "2026-11-21"))
        assertEquals(80, clock.store(today).state.value.energy.battery)
        val yesterday = InMemoryPersistence(settings = Settings(sleepHours = 5.0, sleepDay = "2026-11-20", hapticsOn = false))
        val s = clock.store(yesterday).state.value
        assertEquals(100, s.energy.battery)
        assertEquals(7.0, s.settings.sleepHours)
        assertEquals(false, s.settings.hapticsOn, "other settings are kept")
    }

    @Test fun demoReplayResetsTheDayButNotTheLlmCount() {
        val store = clock.store(demo = true)
        store.onCoach(null, llmCallCount = 1)
        store.onFatigue(FatigueState(85, Level.EXHAUSTED, emptyList(), true), clock.now)
        store.onGuardBlock()
        store.run(clock, 60_000) { store.onVision(face(it)) }
        assertTrue(store.state.value.energy.battery < 90)
        store.resetRun()
        val s = store.state.value
        assertEquals(100, s.energy.battery)
        assertEquals(0, s.stats.focusedMs)
        assertEquals(0, s.stats.phishingBlocked)
        assertEquals(0, s.stats.framesProcessed)
        assertEquals(1, s.stats.llmCalls)
        store.onCoach(null, llmCallCount = 2)
        assertEquals(2, store.state.value.stats.llmCalls)
    }
}
