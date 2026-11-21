package ch.lauzhack.energyguard.app

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnergyBatteryTest {
    private val clock = TestClock()
    private fun face(ts: Long) = VisionFrame(ts = ts, face = true)
    private val typing = InputStats(30_000, 2_000.0, 3, 1.1, 0.2, 40, 0.05, 200.0)
    private val idle = InputStats(30_000, 0.0, 0, 1.0, 0.0, 0, 0.0, 0.0)

    @Test fun sleepMapping() {
        assertEquals(100.0, EnergyBattery.startFromSleep(7.0))
        assertEquals(100.0, EnergyBattery.startFromSleep(9.0))
        assertEquals(90.0, EnergyBattery.startFromSleep(6.0))
        assertEquals(80.0, EnergyBattery.startFromSleep(5.0))
        assertEquals(60.0, EnergyBattery.startFromSleep(3.0))
        assertEquals(60.0, EnergyBattery.startFromSleep(0.0), "clamped below 3 h")
        assertEquals(100.0, EnergyBattery.startFromSleep(Double.NaN))
    }

    @Test fun drainPerLevelAndRefill() {
        assertEquals(95.0, EnergyBattery.step(100.0, HOUR, Level.FRESH, onBreak = false), 1e-9)
        assertEquals(95.0, EnergyBattery.step(100.0, HOUR, Level.OK, onBreak = false), 1e-9)
        assertEquals(90.0, EnergyBattery.step(100.0, HOUR, Level.TIRED, onBreak = false), 1e-9)
        assertEquals(85.0, EnergyBattery.step(100.0, HOUR, Level.EXHAUSTED, onBreak = false), 1e-9)
        assertEquals(80.0, EnergyBattery.step(50.0, HOUR, Level.EXHAUSTED, onBreak = true), 1e-9, "break refills +30 %/h")
        assertEquals(100.0, EnergyBattery.step(95.0, HOUR, Level.FRESH, onBreak = true), "clamped at 100")
        assertEquals(0.0, EnergyBattery.step(5.0, 2 * HOUR, Level.EXHAUSTED, onBreak = false), "clamped at 0")
    }

    @Test fun storeDrainsFasterWhenTired() {
        val store = clock.store()
        store.run(clock, HOUR) { store.onVision(face(it)) }
        assertEquals(95, store.state.value.energy.battery)
        store.onFatigue(FatigueState(70, Level.TIRED, emptyList(), true), clock.now)
        store.run(clock, HOUR) { store.onVision(face(it)) }
        assertEquals(85, store.state.value.energy.battery)
    }

    @Test fun startsFromSleepAndShiftsWhenSleepChanges() {
        val store = clock.store()
        store.setSleepHours(5.0)
        assertEquals(80, store.state.value.energy.battery)
        store.run(clock, HOUR) { store.onVision(face(it)) }
        assertEquals(75, store.state.value.energy.battery)
        store.setSleepHours(7.0)
        assertEquals(95, store.state.value.energy.battery, "same drain, +20 for the better night")
        assertEquals(100, store.state.value.energy.startFromSleep)
    }

    @Test fun autoBreakAfterTwoIdleMinutesRefills() {
        val store = clock.store()
        store.setSleepHours(5.0) // start at 80 so the refill is not hidden by the 100 cap
        store.run(clock, 25 * MIN) { store.onInput(typing, it) }
        assertEquals(78, store.state.value.energy.battery) // 80 - 2.08
        assertFalse(store.state.value.energy.onBreak)

        store.run(clock, 119_000) { store.onInput(idle, it) } // no face, no input: not a break yet
        assertFalse(store.state.value.energy.onBreak)
        store.run(clock, 20 * MIN) { store.onInput(idle, it) }
        val s = store.state.value
        assertTrue(s.energy.onBreak)
        assertEquals(BreakSource.AUTO, s.energy.breakSource)
        assertEquals(1, s.stats.breaksTaken)
        // 77.92 - 119 s of drain (0.17) + 1200 s of refill at 30 %/h (+10) = 87.75
        assertEquals(88, s.energy.battery)
        assertEquals(25 * MIN + 119_000, s.stats.focusedMs, "the first 2 idle minutes still count, the break does not")

        store.onInput(typing, clock.now + 1) // back at the desk
        store.tick(clock.now + 1)
        assertFalse(store.state.value.energy.onBreak)
        assertNull(store.state.value.energy.breakSource)
    }

    @Test fun userBreakUntilBack() {
        val store = clock.store()
        store.startBreak()
        assertEquals(BreakSource.USER, store.state.value.energy.breakSource)
        store.run(clock, 10 * MIN) { store.onInput(typing, it) } // activity does not end a user break
        assertTrue(store.state.value.energy.onBreak)
        store.endBreak()
        assertFalse(store.state.value.energy.onBreak)
        store.run(clock, 3 * MIN) { store.onVision(face(it)) }
        assertEquals(1, store.state.value.stats.breaksTaken, "counted once, no auto-break right after 'I'm back'")
    }

    @Test fun demoModeAcceleratesBatteryTime() {
        val store = clock.store(demo = true)
        store.run(clock, 30_000) { store.onVision(face(it)) } // 30 s real = 1 h battery time
        assertEquals(95, store.state.value.energy.battery)
        assertEquals(30_000, store.state.value.stats.focusedMs, "stats stay in real time")
        assertTrue(store.state.value.demo)
    }

    @Test fun suspendedAppNeverJumps() {
        val store = clock.store()
        store.tick(clock.now)
        clock.now += 5 * HOUR
        store.onVision(face(clock.now))
        store.tick(clock.now)
        assertEquals(100, store.state.value.energy.battery, "one step is capped at 60 s")
        assertEquals(EnergyBattery.MAX_STEP_MS, store.state.value.stats.focusedMs)
    }
}
