package ch.lauzhack.energyguard.guardrail

import androidx.compose.runtime.staticCompositionLocalOf
import ch.lauzhack.energyguard.model.FatigueState

private val fallbackGuardrail = Guardrail()

/**
 * What the UI calls before a risky action: `LocalGuard.current(action)`.
 * main.kt provides the real one (latest FatigueState + haptic WARNING on Block).
 * Default (Android, previews): same checks as if the user were fresh, no haptics.
 */
val LocalGuard = staticCompositionLocalOf<(GuardedAction) -> Verdict> {
    { action -> fallbackGuardrail.check(action, FatigueState.Initial) }
}
