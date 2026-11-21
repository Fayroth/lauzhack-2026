package ch.lauzhack.energyguard.ui.today

import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveSignalsTest {
    private val input = InputStats(30_000, 1000.0, 5, overshootRatio = 1.25, jitter = 2.1, keyCount = 40, backspaceRatio = 0.06, meanInterKeyMs = 192.4)

    @Test fun sourceLabel() {
        assertEquals("Demo data", sensingSource("logiinsight-rgb", demo = true))
        assertEquals("Logitech webcam", sensingSource("logiinsight-rgb", demo = false))
        assertEquals("Logitech webcam · IR", sensingSource("logiinsight-ir", demo = false))
        assertEquals("MediaPipe", sensingSource("mediapipe", demo = false))
        assertEquals("Webcam", sensingSource("mock", demo = false))
        assertEquals("Webcam", sensingSource(null, demo = false))
    }

    @Test fun eyes() {
        assertFalse(eyesLine(null).live)
        assertEquals("no face", eyesLine(VisionFrame(ts = 0, face = false)).flag)
        val l = eyesLine(VisionFrame(ts = 0, face = true, blinkCount = 14, perclos = 0.083, eyesClosed = true))
        assertEquals("14 blinks/min · PERCLOS 8 %", l.text)
        assertEquals("closed", l.flag)
        assertTrue(l.flagWarn)
    }

    @Test fun mouseVsBaseline() {
        assertEquals("Jitter 2.1/s · precision 80 %", mouseLine(input, BaselineSummary.Empty).text)
        val l = mouseLine(input, BaselineSummary(mouseJitter = 1.0))
        assertEquals("Jitter 2.1× usual · precision 80 %", l.text)
        assertEquals("shaky", l.flag)
        assertNull(mouseLine(input.copy(jitter = 1.1), BaselineSummary(mouseJitter = 1.0)).flag)
    }

    @Test fun keyboardTimingsOnly() {
        assertEquals("192 ms/key · backspace 6 %", keyboardLine(input).text)
        val idle = keyboardLine(input.copy(keyCount = 0))
        assertFalse(idle.live)
        assertEquals("not typing", idle.flag)
        assertFalse(keyboardLine(null).live)
    }
}
