package ch.lauzhack.energyguard.app

const val MIN = 60_000L
const val HOUR = 3_600_000L
const val DAY = 86_400_000L

/** Deterministic clock for the store tests: day 0 = 2026-11-21, starts at 08:00. */
class TestClock(var now: Long = 8 * HOUR) {
    fun day(ts: Long): String = "2026-11-" + (21 + ts / DAY).toString().padStart(2, '0')

    fun store(
        persistence: AppPersistence = InMemoryPersistence(),
        demo: Boolean = false,
        persistHistory: Boolean = !demo,
    ) = AppStore({ now }, ::day, persistence, demo = demo, persistHistory = persistHistory, syncSleepPrior = false)
}

/** Advance [ms] in 1 s ticks (like the shells), calling [each] before every tick (e.g. to feed activity). */
fun AppStore.run(clock: TestClock, ms: Long, stepMs: Long = 1_000, each: (Long) -> Unit = {}) {
    val end = clock.now + ms
    while (clock.now < end) {
        clock.now += stepMs
        each(clock.now)
        tick(clock.now)
    }
}
