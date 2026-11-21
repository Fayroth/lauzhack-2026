package ch.lauzhack.energyguard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The old Warm palette (break alert, inbox) follows the app theme. */
class WarmPaletteTest {
    @Test
    fun themeSwapsThePalette() {
        Warm.light = false
        val dark = Warm.Night
        Warm.light = true
        assertNotEquals(dark, Warm.Night)
        assertEquals(DaylightPalette.text, Warm.Text)
        Warm.light = false
        assertEquals(EveningPalette.text, Warm.Text)
    }
}
