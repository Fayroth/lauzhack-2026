package ch.lauzhack.energyguard.input

import ch.lauzhack.energyguard.demoTiredness
import ch.lauzhack.energyguard.demo.mockInputStats
import ch.lauzhack.energyguard.model.InputStats
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

/** Fake stats every second, getting "tired" over time ([tiredness] in 0..1). */
class MockInputSource(private val tiredness: () -> Double = ::demoTiredness) : InputSource {
    override fun stats(): Flow<InputStats> = flow {
        while (true) {
            emit(mockInputStats(tiredness()))
            delay(1000)
        }
    }
}
