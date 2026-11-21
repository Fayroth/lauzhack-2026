package ch.lauzhack.energyguard.ui.components

import ch.lauzhack.energyguard.model.Level
import kotlin.math.abs
import kotlin.math.roundToInt

/** Words shown on screen (pure, unit-tested). The engine's own strings stay unchanged. */

fun levelLabel(level: Level): String = when (level) {
    Level.FRESH -> "Fresh"
    Level.OK -> "OK"
    Level.TIRED -> "Tired"
    Level.EXHAUSTED -> "Exhausted"
}

/** One or two words, shown big next to the gauge. */
fun levelHint(level: Level): String = when (level) {
    Level.FRESH -> "Sharp"
    Level.OK -> "Steady"
    Level.TIRED -> "Fading"
    Level.EXHAUSTED -> "Rest now"
}

/** The sentence under [levelHint]. */
fun levelSentence(level: Level): String = when (level) {
    Level.FRESH -> "You're at your best. Good time for hard work."
    Level.OK -> "Working normally."
    Level.TIRED -> "Your focus is slipping. A short break would help."
    Level.EXHAUSTED -> "Mistakes get likely now. Step away for a few minutes."
}

private val trailingPercent = Regex("""^(.*?)\s([+-]\d+)%$""")

/**
 * The engine's reason ("eyes closing +347%") in plain words ("Eyes closing 4.5× more than usual").
 * Unknown shapes pass through, capitalized.
 */
fun plainReason(reason: String): String {
    val m = trailingPercent.find(reason.trim()) ?: return reason.trim().replaceFirstChar { it.uppercase() }
    val what = m.groupValues[1]
    val pct = m.groupValues[2].toInt()
    val more = pct >= 0
    // "×4.5" reads faster than "+347 %" from the back of a room; small changes stay in percent.
    val amount = if (more && pct >= 50) "${factor(pct)}×" else "${abs(pct)}%"
    return when (what) {
        "eyes closing" -> "Eyes closing $amount ${if (more) "more" else "less"} than usual"
        "long blinks" -> "Blinks $amount ${if (more) "longer" else "shorter"} than usual"
        "blink rate" -> "Blinking $amount ${if (more) "more" else "less"} than usual"
        "shaky mouse" -> "Mouse $amount ${if (more) "shakier" else "steadier"} than usual"
        "mouse overshoot" -> "Mouse overshooting $amount ${if (more) "more" else "less"} than usual"
        "more typos" -> "Typos $amount ${if (more) "more" else "less"} than usual"
        "slower typing" -> "Typing $amount ${if (more) "slower" else "faster"} than usual"
        else -> "${what.replaceFirstChar { it.uppercase() }} ${if (more) "+" else "-"}${abs(pct)}%"
    }
}

/** +347 % -> "4.5", +100 % -> "2". */
private fun factor(pct: Int): String {
    val tenths = ((100 + pct) / 10.0).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** One piece of a duration: a big number and a small unit ("1" "h", "12" "m"). */
data class DurationPart(val number: String, val unit: String)

/** < 1 min: seconds; < 1 h: minutes; else hours + minutes. */
fun durationParts(ms: Long): List<DurationPart> {
    val s = (ms.coerceAtLeast(0) / 1000)
    return when {
        s < 60 -> listOf(DurationPart("$s", "s"))
        s < 3600 -> listOf(DurationPart("${s / 60}", "min"))
        else -> listOf(DurationPart("${s / 3600}", "h"), DurationPart("${(s % 3600) / 60}", "m"))
    }
}

/** 7.0 -> "7 h", 6.5 -> "6.5 h". */
fun hoursText(h: Double): String {
    val halves = (h * 2).roundToInt()
    return if (halves % 2 == 0) "${halves / 2} h" else "${halves / 2}.5 h"
}
