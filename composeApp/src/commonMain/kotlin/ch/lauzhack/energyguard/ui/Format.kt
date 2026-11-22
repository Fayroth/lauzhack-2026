package ch.lauzhack.energyguard.ui

import androidx.compose.ui.graphics.Color
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import kotlin.math.round

/** Display helpers shared by Today (desktop + phone), the break alert and the logo. Pure, unit-tested. */

/** "last minute" (real engine, 60 s window) or "last 10 s" (demo mode). */
internal fun windowLabel(windowMs: Long): String {
    val s = windowMs / 1000
    return when {
        s == 60L -> "last minute"
        s % 60 == 0L -> "last ${s / 60} min"
        else -> "last $s s"
    }
}

/**
 * The engine says "eyes closing +347%"; on screen a factor reads faster: "eyes closing ×4.5 vs usual".
 * Display only, the engine's strings are unchanged. Reasons without a signed percentage pass through.
 */
private val signedPercent = Regex(""" ([+-]\d+)%$""")

/** Same idea inside free text (the coach's message): every "+347%" becomes "×4.5 vs usual". */
private val signedPercentAnywhere = Regex("""([+-])(\d+)\s?%""")

internal fun factorize(text: String): String = signedPercentAnywhere.replace(text) { m ->
    val pct = m.groupValues[2].toInt() * (if (m.groupValues[1] == "-") -1 else 1)
    "×${(1 + pct / 100.0).coerceAtLeast(0.0).fmt(1)} vs usual"
}

internal fun readableReason(reason: String): String {
    val match = signedPercent.find(reason) ?: return reason
    val factor = 1 + match.groupValues[1].toInt() / 100.0
    return reason.substring(0, match.range.first) + " ×${factor.coerceAtLeast(0.0).fmt(1)} vs usual"
}

internal fun Double.fmt(decimals: Int = 2): String {
    var m = 1.0
    repeat(decimals) { m *= 10 }
    val r = round(this * m) / m
    return if (decimals == 0) r.toLong().toString() else r.toString()
}

/** Color of the user's state (break alert, logo): the energy scale, except EXHAUSTED is always red. */
fun stateColor(f: FatigueState): Color = if (f.level == Level.EXHAUSTED) Warm.Rose else energyColor(f.concentration)

fun energyColor(energy: Int): Color = when {
    energy >= 75 -> Warm.Honey
    energy >= 50 -> Warm.Apricot
    energy >= 25 -> Warm.Coral
    else -> Warm.Rose
}
