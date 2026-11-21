package ch.lauzhack.energyguard.input

import ch.lauzhack.energyguard.demoTiredness
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.noise
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Mouse + keyboard statistics (owner: Zyad).
 * Real implementation: global hooks with JNativeHook (com.github.kwhat:jnativehook, already a dependency),
 * aggregated over a rolling window. Record timings/counts only, NEVER key contents.
 */
interface InputSource {
    fun stats(): Flow<InputStats>
}

/** Fake stats every second, getting "tired" over time ([tiredness] in 0..1). */
class MockInputSource(private val tiredness: () -> Double = ::demoTiredness) : InputSource {
    override fun stats(): Flow<InputStats> = flow {
        while (true) {
            emit(mockInputStats(tiredness()))
            delay(1000)
        }
    }
}

/** One fake 30 s window of stats for a given tiredness (0 = fresh, 1 = exhausted). Also used by the demo-mode test. */
internal fun mockInputStats(tired: Double, rnd: Random = Random.Default) = InputStats(
    windowMs = 30_000,
    mouseDistancePx = 8_000 + noise(1_000.0, rnd),
    clickCount = (20 + noise(4.0, rnd)).toInt(),
    overshootRatio = 1.15 + 0.4 * tired + noise(0.03, rnd),
    jitter = 1.0 + 1.5 * tired + noise(0.1, rnd),
    keyCount = (120 - 40 * tired + noise(10.0, rnd)).toInt(),
    backspaceRatio = 0.05 + 0.10 * tired + noise(0.01, rnd),
    meanInterKeyMs = 180 + 120 * tired + noise(10.0, rnd),
)
