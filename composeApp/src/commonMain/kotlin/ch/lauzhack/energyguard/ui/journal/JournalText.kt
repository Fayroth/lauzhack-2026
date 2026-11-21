package ch.lauzhack.energyguard.ui.journal

import ch.lauzhack.energyguard.app.JournalEntry
import ch.lauzhack.energyguard.ui.fmt
import kotlin.math.sqrt

/** Words and numbers of the Journal tab (pure, unit-tested). KSS labels + Pearson r adapted from Mehdi's mehdi/kss (#32). */

/** Karolinska Sleepiness Scale, short words: 1 = very alert ... 9 = fighting sleep. */
val KSS_LABELS = mapOf(
    1 to "very alert", 2 to "alert", 3 to "rather alert", 4 to "slightly alert", 5 to "neither alert nor sleepy",
    6 to "a bit sleepy", 7 to "sleepy", 8 to "very sleepy", 9 to "fighting sleep",
)
val STRESS_LABELS = mapOf(1 to "calm", 2 to "a little stressed", 3 to "stressed", 4 to "very stressed", 5 to "overwhelmed")

/** Entries with a KSS needed before we show a correlation (fewer points = noise). */
const val MIN_COMPARE = 10

/** Pearson correlation; null below 3 points or when one side never varies. */
fun pearson(xs: List<Double>, ys: List<Double>): Double? {
    require(xs.size == ys.size)
    if (xs.size < 3) return null
    val mx = xs.average()
    val my = ys.average()
    val cov = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
    val vx = xs.sumOf { (it - mx) * (it - mx) }
    val vy = ys.sumOf { (it - my) * (it - my) }
    if (vx == 0.0 || vy == 0.0) return null
    return cov / sqrt(vx * vy)
}

/**
 * Self-rated sleepiness vs our fatigue score (100 - concentration) at the same moment: positive r = we agree.
 * Only from [MIN_COMPARE] entries with a KSS.
 */
fun comparisonText(entries: List<JournalEntry>): String {
    val rated = entries.filter { it.kss != null }
    val n = rated.size
    if (n < MIN_COMPARE) return "Compare after $MIN_COMPARE check-ins ($n/$MIN_COMPARE)"
    val r = pearson(rated.map { it.kss!!.toDouble() }, rated.map { (100 - it.concentration).toDouble() })
        ?: return "Not enough variety yet to compare ($n check-ins)"
    val word = when {
        r >= 0.7 -> "strong match"
        r >= 0.4 -> "moderate match"
        r >= 0.2 -> "weak match"
        else -> "no clear match yet"
    }
    return "r = ${r.fmt(2)} over $n check-ins: $word"
}

/** "You said 7/9 · we measured 68" (concentration then); "We measured 68" without a KSS. */
fun measuredLine(e: JournalEntry): String =
    if (e.kss != null) "You said ${e.kss}/9 · we measured ${e.concentration}" else "We measured ${e.concentration}"

/** Chip texts, in this order: sleepiness, stress, caffeine. */
fun entryChips(e: JournalEntry): List<String> = listOfNotNull(
    e.kss?.let { "Sleepy $it/9" },
    e.stress?.let { "Stress $it/5" },
    e.caffeine?.let { cupsText(it) },
)

fun cupsText(n: Int): String = when (n) {
    0 -> "No coffee"
    1 -> "1 cup"
    else -> "$n cups"
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Today 14:32", "Yesterday 09:10", "20 Nov 14:32". [day] / [yesterday] = local "YYYY-MM-DD". */
fun whenText(entryDay: String, time: String, today: String, yesterday: String): String = when (entryDay) {
    today -> "Today $time"
    yesterday -> "Yesterday $time"
    else -> {
        val parts = entryDay.split("-")
        val month = parts.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) }
        val dayOfMonth = parts.getOrNull(2)?.toIntOrNull()
        if (month != null && dayOfMonth != null) "$dayOfMonth $month $time" else "$entryDay $time"
    }
}

/** Save is enabled only when something is filled in (a blank note doesn't count). */
fun canSave(kss: Int?, stress: Int?, caffeine: Int?, note: String): Boolean =
    kss != null || stress != null || caffeine != null || note.isNotBlank()
