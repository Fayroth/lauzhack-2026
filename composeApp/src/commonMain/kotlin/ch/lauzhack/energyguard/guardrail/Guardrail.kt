package ch.lauzhack.energyguard.guardrail

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level

/** A risky action the user is about to take (demo inbox, payment, email...). */
sealed interface GuardedAction {
    data class OpenLink(val url: String, val sender: String) : GuardedAction
    data class Payment(val iban: String, val amountChf: Double) : GuardedAction
    data class SendEmail(val to: String, val hasAttachment: Boolean) : GuardedAction
}

sealed interface Verdict {
    data object Allow : Verdict
    data class Warn(val reason: String) : Verdict
    data class Block(val reason: String) : Verdict
}

/**
 * "We raise the guard exactly when you lower yours."
 * STUB (owner: Ali). TODO: real link reputation (lookalike domains, punycode, text/href mismatch).
 */
class Guardrail(
    private val trustedDomains: Set<String> = setOf("epfl.ch", "lauzhack.com", "github.com"),
) {
    fun check(action: GuardedAction, state: FatigueState): Verdict {
        val tired = state.level >= Level.TIRED
        return when (action) {
            is GuardedAction.OpenLink -> {
                val host = action.url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
                val trusted = trustedDomains.any { host == it || host.endsWith(".$it") }
                when {
                    trusted -> Verdict.Allow
                    tired -> Verdict.Block("You look tired and $host is not a domain you trust. Take a break, check later.")
                    else -> Verdict.Warn("$host is not in your trusted domains.")
                }
            }
            is GuardedAction.Payment ->
                if (tired) Verdict.Warn("Double-check the IBAN: tired people mistype.") else Verdict.Allow
            is GuardedAction.SendEmail ->
                if (tired && action.hasAttachment) Verdict.Warn("Check the recipient before sending an attachment.") else Verdict.Allow
        }
    }
}
