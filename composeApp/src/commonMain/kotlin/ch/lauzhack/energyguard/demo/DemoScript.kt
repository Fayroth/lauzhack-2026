package ch.lauzhack.energyguard.demo

import ch.lauzhack.energyguard.engine.FatigueEngine
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import kotlin.random.Random

/**
 * Demo mode (issue #10 desktop `EG_DEMO=1`, issue #41 Android): a scripted fatigue ramp fed through the SAME pipeline
 * (sample builders below -> FatigueEngine -> gauge, reasons, coach, haptics, guardrail), so the live demo never depends
 * on someone actually looking tired. Only the sensor values are scripted; scoring is the real engine.
 * Shared by desktop and Android (pure Kotlin, no clock: callers pass elapsed ms).
 *
 * Timeline (seconds since start / since replay): 0-15 calibration, FRESH until ~20, smooth drop,
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

/** One fake vision frame for a given tiredness (0 = fresh, 1 = exhausted). Used by the mock sources and the demo. */
fun mockVisionFrame(ts: Long, tired: Double, rnd: Random = Random.Default): VisionFrame {
    val ear = 0.30 - 0.10 * tired + noise(0.01, rnd)
    return VisionFrame(
        ts = ts,
        face = true,
        ear = ear,
        eyesClosed = ear < 0.21,
        blinkCount = (14 + 10 * tired + noise(2.0, rnd)).toInt(),
        lastBlinkMs = (160 + 300 * tired + noise(20.0, rnd)).toInt(),
        perclos = 0.05 + 0.30 * tired + noise(0.01, rnd),
        yawn = tired > 0.8 && rnd.nextDouble() < 0.01,
        headPitch = -4.0 - 10 * tired + noise(1.0, rnd),
        headYaw = noise(3.0, rnd),
        source = "mock",
    )
}

/** One fake 30 s window of input stats for a given tiredness (0 = fresh, 1 = exhausted). */
fun mockInputStats(tired: Double, rnd: Random = Random.Default) = InputStats(
    windowMs = 30_000,
    mouseDistancePx = 8_000 + noise(1_000.0, rnd),
    clickCount = (20 + noise(4.0, rnd)).toInt(),
    overshootRatio = 1.15 + 0.4 * tired + noise(0.03, rnd),
    jitter = 1.0 + 1.5 * tired + noise(0.1, rnd),
    keyCount = (120 - 40 * tired + noise(10.0, rnd)).toInt(),
    backspaceRatio = 0.05 + 0.10 * tired + noise(0.01, rnd),
    meanInterKeyMs = 180 + 120 * tired + noise(10.0, rnd),
)

internal fun noise(amplitude: Double, rnd: Random = Random.Default): Double = (rnd.nextDouble() * 2 - 1) * amplitude
