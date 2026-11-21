package ch.lauzhack.energyguard.guardrail

import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level

/** A risky action the user is about to take. Contract in docs/API.md (update it FIRST). */
sealed interface GuardedAction {
    /** A link click: [displayText] = what the user sees, [href] = where it really goes, [context] = email subject + body. */
    data class OpenLink(val displayText: String, val href: String, val context: String = "") : GuardedAction
    data class Payment(val iban: String, val amountChf: Double) : GuardedAction
}

sealed interface Verdict {
    data object Allow : Verdict
    /** Ask for confirmation. [reason] = headline, [signals] = one plain-words line per red flag. */
    data class Warn(val reason: String, val signals: List<String> = emptyList()) : Verdict
    /** No way through. */
    data class Block(val reason: String, val signals: List<String> = emptyList()) : Verdict
}

/**
 * "We raise the guard exactly when you lower yours."
 * Pure, deterministic link checks (no ML, no network). Strong signals block when TIRED/EXHAUSTED and only warn when FRESH/OK;
 * weak signals warn when tired and are ignored when fresh.
 */
class Guardrail(
    trustedDomains: Set<String> = setOf("epfl.ch", "lauzhack.com", "github.com"),
) {
    /** Brand name → its official registrable domains. Used for lookalike detection; all official domains are trusted. */
    private val brands: Map<String, Set<String>> = mapOf(
        "paypal" to setOf("paypal.com", "paypal.ch"),
        "google" to setOf("google.com", "google.ch", "youtube.com", "gmail.com"),
        "microsoft" to setOf("microsoft.com", "live.com", "office.com", "outlook.com"),
        "outlook" to setOf("outlook.com", "live.com", "office.com", "microsoft.com"),
        "apple" to setOf("apple.com", "icloud.com"),
        "icloud" to setOf("icloud.com", "apple.com"),
        "amazon" to setOf("amazon.com", "amazon.de", "amazon.fr"),
        "netflix" to setOf("netflix.com"),
        "github" to setOf("github.com", "github.io"),
        "epfl" to setOf("epfl.ch"),
        "postfinance" to setOf("postfinance.ch"),
        "swisscom" to setOf("swisscom.ch", "swisscom.com"),
        "ubs" to setOf("ubs.com", "ubs.ch"),
        "raiffeisen" to setOf("raiffeisen.ch"),
        "twint" to setOf("twint.ch"),
        "logitech" to setOf("logitech.com", "logi.com"),
        "jetbrains" to setOf("jetbrains.com"),
        "axa" to setOf("axa.ch", "axa.com"),
        "lauzhack" to setOf("lauzhack.com"),
    )
    private val trusted: Set<String> = trustedDomains.map { it.lowercase() }.toSet() + brands.values.flatten()

    fun check(action: GuardedAction, fatigue: FatigueState): Verdict {
        val tired = fatigue.level >= Level.TIRED
        return when (action) {
            is GuardedAction.OpenLink -> decide(analyzeLink(action), tired, fatigue)
            is GuardedAction.Payment ->
                if (tired) Verdict.Warn(
                    "You look tired: double-check the IBAN and the amount (${action.amountChf} CHF) before paying.",
                    listOf("Tired people mistype numbers"),
                ) else Verdict.Allow
        }
    }

    private class Findings(val host: String, val strong: List<String>, val weak: List<String>)

    private fun decide(f: Findings, tired: Boolean, fatigue: FatigueState): Verdict {
        val all = f.strong + f.weak
        return when {
            f.strong.isNotEmpty() && tired -> Verdict.Block(
                "Blocked: this looks like phishing and your energy is low (${fatigue.energy}%). Take a break, check it later.",
                all,
            )
            f.strong.isNotEmpty() -> Verdict.Warn("This link looks suspicious: ${f.strong.first().decap()}.", all)
            f.weak.isNotEmpty() && tired -> Verdict.Warn(
                "Your energy is low (${fatigue.energy}%): are you sure you want to open ${f.host}?",
                all,
            )
            else -> Verdict.Allow
        }
    }

    // --- Link analysis -------------------------------------------------------------------------------------------

    private fun analyzeLink(link: GuardedAction.OpenLink): Findings {
        val url = parseUrl(link.href)
        val host = url.host
        val strong = mutableListOf<String>()
        val weak = mutableListOf<String>()

        if (host.isEmpty()) {
            return Findings(link.href, listOf("The link has no readable web address"), emptyList())
        }
        val isIp = IPV4.matches(host) || host.startsWith("[") || (host.count { it == ':' } >= 2)
        val domain = if (isIp) host else registrable(host)

        if (url.hasUserInfo) {
            strong += "The address hides its real destination: everything before '@' is ignored, it really goes to $host"
        }
        if (isIp) strong += "The link goes to a raw IP address ($host) instead of a named website"
        if (host.split('.').any { it.startsWith("xn--") } || host.any { it.code > 127 }) {
            strong += "The address uses look-alike international letters (punycode: $host)"
        }

        // Display text shows a domain that is not the real one.
        val shown = DOMAIN_IN_TEXT.find(link.displayText.lowercase())?.value?.trimEnd('.')
        if (shown != null && !isIp && shown.substringAfterLast('.') !in FILE_EXTENSIONS && registrable(shown) != domain) {
            strong += "The link text says ${registrable(shown)} but it actually goes to $domain"
        }

        // Lookalike / brand impersonation in the host.
        if (!isIp) {
            lookalike(host, domain)?.let { strong += it }
        }
        // Brand named in the visible text but the link goes elsewhere (e.g. "Log in to PayPal" -> evil.xyz).
        if (!isIp && strong.none { it.startsWith("The link text says") }) {
            val words = WORD.findAll(link.displayText.lowercase()).map { it.value }.toSet()
            brands.entries.firstOrNull { (b, official) -> b in words && domain !in official }?.let { (b, official) ->
                strong += "The link mentions ${b.replaceFirstChar { it.uppercase() }} but goes to $domain, not ${official.first()}"
            }
        }

        // Weak signals.
        if (domain !in trusted) weak += "$domain is not a site you usually trust"
        if (url.scheme == "http") weak += "The connection is not encrypted (http, not https)"
        if (domain in SHORTENERS) weak += "A link shortener hides the real destination"
        val tld = host.substringAfterLast('.')
        if (!isIp && tld in CHEAP_TLDS) weak += "Unusual domain ending (.$tld), often used by scammers"
        val text = (link.displayText + " " + link.context).lowercase()
        val urgent = URGENCY.filter { it in text }
        if (urgent.isNotEmpty()) weak += "Pressure words: \"${urgent.take(3).joinToString("\", \"")}\""

        return Findings(if (isIp) host else domain, strong.distinct(), weak)
    }

    /** Returns a strong-signal sentence if [host] imitates a known brand without being on its official domain. */
    private fun lookalike(host: String, domain: String): String? {
        if (domain in trusted) return null
        val labels = host.split('.').dropLast(1) // drop the TLD
        for (label in labels) {
            for (token in label.split('-').filter { it.isNotEmpty() } + label.replace("-", "")) {
                val norm = normalize(token)
                for ((brand, official) in brands) {
                    val hit = norm == brand ||
                        (brand.length >= 5 && brand in norm) ||
                        (brand.length >= 5 && norm.length >= 5 && editDistance(norm, brand) <= 1)
                    if (!hit) continue
                    val imitation = norm != token || brand !in norm // digits/rn swapped, or one letter off
                    return if (imitation) {
                        "$domain imitates ${official.first()} (look-alike letters)"
                    } else {
                        "The address uses the name '$brand' but the real site is $domain, not ${official.first()}"
                    }
                }
            }
        }
        return null
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace("rn", "m").replace("vv", "w")
        .map { CONFUSABLE_DIGITS[it] ?: it }.joinToString("")

    private class Url(val scheme: String, val host: String, val hasUserInfo: Boolean)

    private fun parseUrl(raw: String): Url {
        val s = raw.trim()
        val scheme = if ("://" in s) s.substringBefore("://").lowercase() else ""
        val rest = if ("://" in s) s.substringAfter("://") else s
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val hasUserInfo = '@' in authority
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]"
        else hostPort.substringBefore(':')
        return Url(scheme, host.lowercase().trimEnd('.'), hasUserInfo)
    }

    /** Approximate registrable domain without a public-suffix list: last 2 labels, or 3 for "co.uk"-style suffixes. */
    private fun registrable(host: String): String {
        val labels = host.lowercase().removePrefix("www.").split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val secondLevel = labels[labels.size - 2]
        val n = if (labels.last().length == 2 && secondLevel in SECOND_LEVEL) 3 else 2
        return labels.takeLast(n).joinToString(".")
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun String.decap() = replaceFirstChar { it.lowercase() }

    private companion object {
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        val DOMAIN_IN_TEXT = Regex("""\b([a-z0-9-]+\.)+[a-z]{2,}\b""")
        val WORD = Regex("""[a-z0-9]+""")
        val CONFUSABLE_DIGITS = mapOf('0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '$' to 's')
        val SECOND_LEVEL = setOf("co", "com", "org", "net", "ac", "gov", "edu")
        val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "cutt.ly", "rebrand.ly")
        val FILE_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "jpeg", "txt", "htm", "html")
        val CHEAP_TLDS = setOf("xyz", "top", "zip", "mov", "click", "link", "work", "support", "info", "ru", "tk", "gq", "cf", "ml")
        val URGENCY = listOf(
            "urgent", "immediately", "suspended", "locked", "verify your account", "confirm your account", "24 hours", "final notice", "last warning", "act now", "unusual activity", "password expires",
            "immédiatement", "suspendu", "bloqué", "dringend", "gesperrt",
        )
    }
}
