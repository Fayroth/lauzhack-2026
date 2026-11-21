package ch.lauzhack.energyguard.input

import ch.lauzhack.energyguard.demoTiredness
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.noise
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mouse + keyboard statistics (owner: Zyad).
 * Real implementation: global hooks with JNativeHook (com.github.kwhat:jnativehook, already a dependency),
 * aggregated over a rolling window. Record timings/counts only, NEVER key contents.
 */
interface InputSource {
    fun stats(): Flow<InputStats>
}

/** Fake stats every second, getting "tired" over time. */
class MockInputSource : InputSource {
    override fun stats(): Flow<InputStats> = flow {
        while (true) {
            val tired = demoTiredness()
            emit(
                InputStats(
                    windowMs = 30_000,
                    mouseDistancePx = 8_000 + noise(1_000.0),
                    clickCount = (20 + noise(4.0)).toInt(),
                    overshootRatio = 1.15 + 0.4 * tired + noise(0.03),
                    jitter = 1.0 + 1.5 * tired + noise(0.1),
                    keyCount = (120 - 40 * tired + noise(10.0)).toInt(),
                    backspaceRatio = 0.05 + 0.10 * tired + noise(0.01),
                    meanInterKeyMs = 180 + 120 * tired + noise(10.0),
                )
            )
            delay(1000)
        }
    }
}
