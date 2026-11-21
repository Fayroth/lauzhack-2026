package ch.lauzhack.energyguard.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecoveryScoreTest {
    private fun entry(kss: Int? = null, stress: Int? = null, caffeine: Int? = null) =
        JournalEntry(id = 1, ts = 0, kss = kss, stress = stress, caffeine = caffeine, concentration = 80)

    @Test fun components() {
        assertEquals(0.0, RecoveryScore.sleepComponent(4.0))
        assertEquals(50.0, RecoveryScore.sleepComponent(6.0))
        assertEquals(100.0, RecoveryScore.sleepComponent(9.0))
        assertEquals(0, RecoveryScore.breaksNeeded(59 * MIN))
        assertEquals(2, RecoveryScore.breaksNeeded(2 * HOUR + 30 * MIN))
        assertEquals(100.0, RecoveryScore.breaksComponent(taken = 0, needed = 0), "nothing needed yet")
        assertEquals(50.0, RecoveryScore.breaksComponent(taken = 1, needed = 2))
        assertEquals(100.0, RecoveryScore.breaksComponent(taken = 5, needed = 2), "capped")
        assertEquals(100.0, RecoveryScore.checkInComponent(entry(kss = 1)))
        assertEquals(0.0, RecoveryScore.checkInComponent(entry(kss = 9)))
        assertEquals(50.0, RecoveryScore.checkInComponent(entry(kss = 1, stress = 5)), "mean of KSS and stress")
        assertNull(RecoveryScore.checkInComponent(entry(caffeine = 2)), "caffeine alone is not a check-in")
        assertNull(RecoveryScore.checkInComponent(null))
    }

    @Test fun weights() {
        // sleep 6 h = 50, breaks 1/2 = 50, check-in KSS 1 = 100 -> 0.5*50 + 0.3*50 + 0.2*100 = 60
        assertEquals(60, RecoveryScore.compute(6.0, breaksTaken = 1, breaksNeeded = 2, checkIn = entry(kss = 1)))
        // all perfect
        assertEquals(100, RecoveryScore.compute(8.0, 0, 0, entry(kss = 1, stress = 1)))
    }

    @Test fun renormalizedWithoutCheckIn() {
        // sleep 50, breaks 100, no check-in -> (0.5*50 + 0.3*100) / 0.8 = 68.75 -> 69
        assertEquals(69, RecoveryScore.compute(6.0, 0, 0, null))
        assertEquals(69, RecoveryScore.compute(6.0, 0, 0, entry(caffeine = 3)), "entry without KSS/stress = absent")
        // sleep 0, breaks 0/3 -> 0
        assertEquals(0, RecoveryScore.compute(4.0, 0, 3, null))
    }

    @Test fun storeUsesTodaysLatestCheckIn() {
        val clock = TestClock()
        val store = clock.store()
        store.setSleepHours(6.0)
        assertEquals(69, store.state.value.recovery)
        store.addJournalEntry(kss = 1)
        assertEquals(60 + 15, store.state.value.recovery, "0.5*50 + 0.3*100 + 0.2*100 = 75")
        clock.now += 10 * MIN
        store.addJournalEntry(caffeine = 2) // newer, but no KSS/stress: the KSS one still counts
        assertEquals(75, store.state.value.recovery)
        clock.now += DAY
        store.tick(clock.now)
        assertEquals(69, store.state.value.recovery, "yesterday's check-in does not count today")
    }
}
