package ch.lauzhack.energyguard.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class CopyTest {
    @Test fun bigIncreaseReadsAsFactor() {
        assertEquals("Eyes closing 4.5× more than usual", plainReason("eyes closing +347%"))
        assertEquals("Blinks 2× longer than usual", plainReason("long blinks +100%"))
    }

    @Test fun smallChangesStayInPercent() {
        assertEquals("Typing 32% slower than usual", plainReason("slower typing +32%"))
        assertEquals("Blinking 30% less than usual", plainReason("blink rate -30%"))
    }

    @Test fun otherReasonsPassThroughCapitalized() {
        assertEquals("Eyes closed 2.3 s", plainReason("eyes closed 2.3 s"))
        assertEquals("Rubbing eyes", plainReason("rubbing eyes"))
        assertEquals("Looking away 40% of the time", plainReason("looking away 40% of the time"))
    }

    @Test fun durations() {
        assertEquals(listOf(DurationPart("45", "s")), durationParts(45_000))
        assertEquals(listOf(DurationPart("12", "min")), durationParts(12 * 60_000L + 5_000))
        assertEquals(listOf(DurationPart("1", "h"), DurationPart("12", "m")), durationParts(72 * 60_000L))
        assertEquals(listOf(DurationPart("0", "s")), durationParts(-5))
    }

    @Test fun hours() {
        assertEquals("7 h", hoursText(7.0))
        assertEquals("6.5 h", hoursText(6.5))
    }

    @Test fun shortReasonsForTheStateLine() {
        assertEquals(ShortReason("4.5×", "eyes closing"), shortReason("eyes closing +347%"))
        assertEquals(ShortReason("1.8×", "longer blinks"), shortReason("long blinks +80%"))
        assertEquals(ShortReason("−32%", "typing"), shortReason("slower typing +32%"))
        assertEquals(ShortReason("+20%", "mouse shake"), shortReason("shaky mouse +20%"))
        assertEquals(ShortReason("", "rubbing eyes"), shortReason("rubbing eyes"))
    }

    @Test fun compactLabels() {
        assertEquals("1h 12m", durationShort(72 * 60_000L))
        assertEquals("43m", durationShort(43 * 60_000L))
        assertEquals("Sunday 22 November", dayLabel("2026-11-22"))
        assertEquals("Saturday 21 November", dayLabel("2026-11-21"))
        assertEquals("Thursday 1 January", dayLabel("2026-01-01"))
        assertEquals("x", dayLabel("x"))
        assertEquals("08:05", clockLabel(485))
        assertEquals("00:10", clockLabel(1450))
        assertEquals("last 60 s", windowShort(60_000))
    }
}
