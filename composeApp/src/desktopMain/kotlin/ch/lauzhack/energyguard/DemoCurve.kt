package ch.lauzhack.energyguard

import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

private val demoStart = System.currentTimeMillis()

/**
 * Fake "tiredness" in 0..1 used by the mock sources so the gauge visibly moves:
 * fresh for the first ~60 s (calibration), then slowly oscillates (period 2 min).
 * The scripted demo ramp (EG_DEMO=1) lives in demo/DemoMode.kt.
 */
internal fun demoTiredness(): Double {
    val t = (System.currentTimeMillis() - demoStart) / 1000.0
    if (t < 60) return 0.0
    return (1 - cos(2 * PI * (t - 60) / 120)) / 2
}

internal fun noise(amplitude: Double, rnd: Random = Random.Default): Double = (rnd.nextDouble() * 2 - 1) * amplitude
