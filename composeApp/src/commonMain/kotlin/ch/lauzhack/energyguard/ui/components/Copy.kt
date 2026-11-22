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

/** The sentence under the level name. */
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

/** One reason for the hero's state line: [amount] drawn in the state colour, then [what]. */
data class ShortReason(val amount: String, val what: String)

/**
 * The engine's reason in a few words for the inline state line (C2): "eyes closing +347%" -> 4.5× "eyes closing",
 * "long blinks +80%" -> 1.8× "longer blinks", "slower typing +32%" -> −32% "typing". Unknown shapes -> null amount.
 */
fun shortReason(reason: String): ShortReason {
    val m = trailingPercent.find(reason.trim()) ?: return ShortReason("", reason.trim())
    val what = m.groupValues[1]
    val pct = m.groupValues[2].toInt()
    val times = if (pct >= 50) "${factor(pct)}×" else if (pct >= 0) "+$pct%" else "−${abs(pct)}%"
    return when (what) {
        "eyes closing" -> ShortReason(times, "eyes closing")
        "long blinks" -> ShortReason(times, if (pct >= 0) "longer blinks" else "shorter blinks")
        "blink rate" -> ShortReason(times, "blink rate")
        "shaky mouse" -> ShortReason(times, "mouse shake")
        "mouse overshoot" -> ShortReason(times, "mouse overshoot")
        "more typos" -> ShortReason(times, "typos")
        "slower typing" -> ShortReason(if (pct >= 0) "−$pct%" else "+${abs(pct)}%", "typing")
        else -> ShortReason(times, what)
    }
}

/** Compact duration for the quiet stats line: "45s", "43m", "1h 12m". */
fun durationShort(ms: Long): String = durationParts(ms).joinToString(" ") { p ->
    p.number + if (p.unit == "min") "m" else p.unit
}

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
)

/** "2026-11-22" -> "Sunday 22 November"; anything else passes through. */
fun dayLabel(dayKey: String): String {
    val p = dayKey.split("-").mapNotNull { it.toIntOrNull() }
    if (p.size != 3 || p[1] !in 1..12 || p[2] !in 1..31) return dayKey
    val (y, m, d) = p
    // Sakamoto: 0 = Sunday
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val yy = if (m < 3) y - 1 else y
    val dow = (yy + yy / 4 - yy / 100 + yy / 400 + t[m - 1] + d) % 7
    return "${WEEKDAYS[(dow + 6) % 7]} $d ${MONTHS[m - 1]}"
}

/** Minutes after midnight -> "08:05" (wraps at 24 h). */
fun clockLabel(minutesOfDay: Int): String {
    val m = ((minutesOfDay % 1440) + 1440) % 1440
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

/** "last 60 s" (engine window) / "last 10 s" (demo). */
fun windowShort(windowMs: Long): String = "last ${windowMs / 1000} s"
