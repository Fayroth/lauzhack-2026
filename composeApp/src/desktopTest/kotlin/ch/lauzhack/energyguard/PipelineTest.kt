package ch.lauzhack.energyguard

import ch.lauzhack.energyguard.haptics.HapticPattern
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.vision.parseVisionMessage
import ch.lauzhack.energyguard.vision.reconnecting
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PipelineTest {

    @Test
    fun visionFramesAreStampedOnArrival() {
        val msg = """{"type":"vision","ts":42,"face":true,"ear":0.27,"source":"webcam","futureField":1}"""
        val frame = parseVisionMessage(msg, arrivalTs = 1_800_000_000_000L)!!
        assertEquals(1_800_000_000_000L, frame.ts, "sidecar ts must be replaced by the arrival time")
        assertEquals(0.27, frame.ear)
        assertNull(parseVisionMessage("not json", 1L))
        assertNull(parseVisionMessage("""{"type":"hello","ts":1,"face":true}""", 1L))
    }

    @Test
    fun reconnectsAfterFailuresAndClosedSessions() = runBlocking {
        var attempt = 0
        val logs = mutableListOf<String>()
        val flow = reconnecting<Int>(retryMs = 10, log = { logs += it }) { emit ->
            attempt++
            when (attempt) {
                1 -> throw IOException("connection refused") // sidecar not started yet
                2 -> { emit(1); emit(2) }                     // sidecar streams then closes
                3 -> { emit(3); throw IOException("reset") }  // sidecar killed mid-stream
                else -> emit(attempt)
            }
        }
        val got = withTimeout(5_000) { flow.take(4).toList() }
        assertEquals(listOf(1, 2, 3, 4), got)
        assertEquals(2, logs.size, "one log per failure: $logs")
    }

    @Test
    fun hapticOnlyOnTheWayUpWithCooldown() {
        val t = HapticTrigger(cooldownMs = 120_000)
        assertNull(t.onLevel(Level.FRESH, 0))
        assertNull(t.onLevel(Level.OK, 1_000))
        assertEquals(HapticPattern.GENTLE, t.onLevel(Level.TIRED, 2_000))
        assertEquals(HapticPattern.WARNING, t.onLevel(Level.EXHAUSTED, 10_000), "escalation is never swallowed")
        assertNull(t.onLevel(Level.TIRED, 20_000), "going down: no pulse")
        assertNull(t.onLevel(Level.EXHAUSTED, 30_000), "same pattern again inside the 2-min cooldown")
        assertNull(t.onLevel(Level.OK, 40_000))
        assertNull(t.onLevel(Level.TIRED, 50_000), "inside cooldown")
        assertNull(t.onLevel(Level.OK, 200_000))
        assertEquals(HapticPattern.GENTLE, t.onLevel(Level.TIRED, 200_000), "after cooldown")
    }
}
