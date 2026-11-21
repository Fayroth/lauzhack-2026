package ch.lauzhack.energyguard.demo

import ch.lauzhack.energyguard.engine.FatigueEngine

/**
 * Demo mode (issue #10, `EG_DEMO=1`): a scripted fatigue ramp fed through the SAME pipeline
 * (mock sources -> FatigueEngine -> gauge, reasons, haptics, guardrail), so the live demo never depends
 * on someone actually looking tired. Only the sensor values are scripted; scoring is the real engine.
 *
 * Timeline (seconds since start / since F5): 0-15 calibration, FRESH until ~20, smooth drop,
 * TIRED by ~60, EXHAUSTED by ~90, then holds. The UI shows a small "DEMO" marker (honest with the jury).
 */
object DemoScript {
    /** Shorter than the real 60 s so the 2-minute demo has time for the story. */
    const val CALIBRATION_MS = 15_000L
    /** Shorter rolling window so the gauge follows the script within seconds. */
    const val WINDOW_MS = 10_000L

    /** (seconds, tiredness 0..1) keyframes, linear in between, constant after the last one. */
    private val KEYFRAMES = listOf(0.0 to 0.0, 20.0 to 0.0, 50.0 to 0.25, 95.0 to 0.45, 110.0 to 0.6)

    fun tiredness(elapsedMs: Long): Double {
        val t = elapsedMs / 1000.0
        if (t <= KEYFRAMES.first().first) return KEYFRAMES.first().second
        for ((a, b) in KEYFRAMES.zipWithNext()) {
            if (t <= b.first) return a.second + (b.second - a.second) * (t - a.first) / (b.first - a.first)
        }
        return KEYFRAMES.last().second
    }

    fun newEngine() = FatigueEngine(calibrationMs = CALIBRATION_MS, windowMs = WINDOW_MS)
}

/** Wall-clock start of the current demo run; [restart] (F5) replays the script from zero. */
class DemoClock(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var startMs = clock()
    fun restart() { startMs = clock() }
    fun tiredness(): Double = DemoScript.tiredness(clock() - startMs)
}
