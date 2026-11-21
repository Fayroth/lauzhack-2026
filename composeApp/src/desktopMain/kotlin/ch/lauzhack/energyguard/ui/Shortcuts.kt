package ch.lauzhack.energyguard.ui

import androidx.compose.ui.input.key.Key

/** Window shortcuts of the dashboard (main.kt). */
enum class Shortcut { REPLAY_DEMO, OPEN_INBOX, TAB_TODAY, TAB_JOURNAL, TAB_PROFILE }

/** The shortcut for [key], or null. Always null while a text field has focus: those keys are typing. */
fun shortcutFor(key: Key, textFocused: Boolean): Shortcut? = if (textFocused) null else when (key) {
    Key.F5 -> Shortcut.REPLAY_DEMO
    Key.F2 -> Shortcut.OPEN_INBOX
    Key.One, Key.NumPad1 -> Shortcut.TAB_TODAY
    Key.Two, Key.NumPad2 -> Shortcut.TAB_JOURNAL
    Key.Three, Key.NumPad3 -> Shortcut.TAB_PROFILE
    else -> null
}
