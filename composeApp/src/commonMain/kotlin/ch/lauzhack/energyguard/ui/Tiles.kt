package ch.lauzhack.energyguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.VisionFrame
import kotlin.math.round

/** Health of one signal group, shown as a colored dot on its tile. */
enum class SignalHealth(val label: String) {
    NONE("Waiting"),
    GOOD("Normal"),
    WARN("Drifting"),
    BAD("Tired");

    /** Read live (not stored in the enum) so it follows the light/dark toggle. */
    val color: Color
        get() = when (this) {
            NONE -> Warm.Faint
            GOOD -> Warm.Sage
            WARN -> Warm.Apricot
            BAD -> Warm.Rose
        }
}

/** One value line on a tile: name, formatted value, and its own health. */
data class Metric(val name: String, val value: String, val health: SignalHealth)

/**
 * Display-only thresholds so the tiles change color during the demo.
 * The real score comes from FatigueEngine (personal baseline); these are absolute
 * rules of thumb tuned on the mock data ranges.
 */
private fun grade(value: Double?, warn: Double, bad: Double): SignalHealth = when {
    value == null -> SignalHealth.NONE
    value >= bad -> SignalHealth.BAD
    value >= warn -> SignalHealth.WARN
    else -> SignalHealth.GOOD
}

private fun worst(metrics: List<Metric>): SignalHealth =
    metrics.map { it.health }.filter { it != SignalHealth.NONE }.maxByOrNull { it.ordinal } ?: SignalHealth.NONE

fun eyeMetrics(v: VisionFrame?): List<Metric> {
    val face = v?.face == true
    val perclos = if (face) v?.perclos else null
    val blink = if (face) v?.lastBlinkMs?.toDouble() else null
    val rate = if (face) v?.blinkCount?.toDouble() else null
    return listOf(
        Metric("Eyes closed (PERCLOS)", perclos?.let { "${(it * 100).fmt(0)} %" } ?: "-", grade(perclos, 0.12, 0.20)),
        Metric("Last blink", blink?.let { "${it.toLong()} ms" } ?: "-", grade(blink, 250.0, 350.0)),
        Metric("Blinks / min", rate?.toLong()?.toString() ?: "-", grade(rate, 20.0, 26.0)),
    )
}

fun mouseMetrics(i: InputStats?): List<Metric> = listOf(
    Metric("Jitter", i?.jitter?.let { "x${it.fmt(1)}" } ?: "-", grade(i?.jitter, 1.3, 1.8)),
    Metric("Overshoot", i?.overshootRatio?.let { "x${it.fmt(2)}" } ?: "-", grade(i?.overshootRatio, 1.25, 1.4)),
    Metric("Clicks / 30 s", i?.clickCount?.toString() ?: "-", if (i == null) SignalHealth.NONE else SignalHealth.GOOD),
)

fun keyboardMetrics(i: InputStats?): List<Metric> {
    val typing = i != null && i.keyCount > 0
    val backspace = if (typing) i?.backspaceRatio else null
    val interKey = if (typing) i?.meanInterKeyMs else null
    return listOf(
        Metric("Typos (backspace)", backspace?.let { "${(it * 100).fmt(0)} %" } ?: "-", grade(backspace, 0.08, 0.12)),
        Metric("Time between keys", interKey?.let { "${it.toLong()} ms" } ?: "-", grade(interKey, 220.0, 260.0)),
        Metric("Keys / 30 s", if (typing) i!!.keyCount.toString() else "-", if (typing) SignalHealth.GOOD else SignalHealth.NONE),
    )
}

/** A big card for one signal group (Eyes / Mouse / Keyboard), readable from the back of the room. */
@Composable
fun SignalTile(title: String, subtitle: String, metrics: List<Metric>, modifier: Modifier = Modifier) {
    val health = worst(metrics)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .background(Warm.Surface, shape)
            // A whisper of the signal's color in the card itself, instead of a hard outline.
            .background(health.color.copy(alpha = if (health == SignalHealth.GOOD || health == SignalHealth.NONE) 0.04f else 0.10f), shape)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
            Spacer(Modifier.width(16.dp))
            Text(subtitle, fontSize = 17.sp, color = Warm.Muted)
            Spacer(Modifier.weight(1f))
            // Tiles show the live value; the gauge shows the last minute. Say so, or "low gauge + calm tiles" looks like a bug.
            Text("now", fontSize = 16.sp, color = Warm.Faint)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.background(health.color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(health.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(health.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = health.color)
            }
        }
        metrics.forEach { m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, fontSize = 20.sp, color = Warm.Muted)
                Spacer(Modifier.weight(1f))
                Text(
                    m.value,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (m.health == SignalHealth.GOOD || m.health == SignalHealth.NONE) Warm.Text else m.health.color,
                )
            }
        }
    }
}

internal fun Double.fmt(decimals: Int = 2): String {
    var m = 1.0
    repeat(decimals) { m *= 10 }
    val r = round(this * m) / m
    return if (decimals == 0) r.toLong().toString() else r.toString()
}
