package ch.lauzhack.energyguard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FrugalityTest {
    @Test
    fun thousandsAreGrouped() {
        assertEquals("0", groupThousands(0))
        assertEquals("999", groupThousands(999))
        assertEquals("12 345", groupThousands(12_345))
        assertEquals("1 234 567", groupThousands(1_234_567))
    }

    @Test
    fun themeToggleSwapsThePalette() {
        Warm.light = false
        val dark = Warm.Night
        Warm.light = true
        assertNotEquals(dark, Warm.Night)
        assertEquals(DaylightPalette.text, Warm.Text)
        Warm.light = false
        assertEquals(EveningPalette.text, Warm.Text)
    }
}
