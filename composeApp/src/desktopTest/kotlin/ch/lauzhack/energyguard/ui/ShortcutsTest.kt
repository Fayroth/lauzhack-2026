package ch.lauzhack.energyguard.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortcutsTest {
    @Test fun keysMapToShortcuts() {
        assertEquals(Shortcut.TAB_JOURNAL, shortcutFor(Key.Two, textFocused = false))
        assertEquals(Shortcut.REPLAY_DEMO, shortcutFor(Key.F5, textFocused = false))
        assertNull(shortcutFor(Key.A, textFocused = false))
    }

    @Test fun ignoredWhileTyping() {
        listOf(Key.One, Key.Two, Key.Three, Key.NumPad1, Key.F2, Key.F5).forEach { assertNull(shortcutFor(it, textFocused = true)) }
    }
}
