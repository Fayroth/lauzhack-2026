package ch.lauzhack.energyguard.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * Whether a text field has keyboard focus. The desktop window's shortcuts (1 / 2 / 3 tabs, F2, F5) are ignored
 * while it is true, so typing "1" in a journal note doesn't switch tabs. UI thread only.
 * Every text field must use [Modifier.tracksTextFocus].
 */
object TextFieldFocus {
    private val owners = mutableSetOf<Any>()
    val active: Boolean get() = owners.isNotEmpty()

    fun set(owner: Any, focused: Boolean) {
        if (focused) owners.add(owner) else owners.remove(owner)
    }
}

/** Put on every text field: reports its focus to [TextFieldFocus] (cleared if the field leaves the screen focused). */
fun Modifier.tracksTextFocus(): Modifier = composed {
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { TextFieldFocus.set(owner, false) } }
    onFocusChanged { TextFieldFocus.set(owner, it.isFocused) }
}
