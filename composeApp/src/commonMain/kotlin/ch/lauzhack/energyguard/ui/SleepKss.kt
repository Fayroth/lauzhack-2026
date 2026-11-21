package ch.lauzhack.energyguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.model.SleepPrior
import kotlinx.coroutines.delay
import kotlin.math.sqrt

// ---------- #15 "Sleep last night" slider ----------

/** "Slept 6.5 h" + slider (3..9 h, half-hour steps). Writes SleepPrior, which the engine reads every frame. */
@Composable
fun SleepSlider(modifier: Modifier = Modifier) {
    var hours by remember { mutableFloatStateOf(SleepPrior.hours.toFloat()) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Slept", fontSize = 18.sp, color = Warm.Muted)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = hours,
            onValueChange = { hours = it; SleepPrior.hours = it.toDouble() },
            valueRange = 3f..9f,
            steps = 11, // half hours
            modifier = Modifier.width(170.dp),
            colors = SliderDefaults.colors(
                thumbColor = Warm.Brand, activeTrackColor = Warm.Brand,
                inactiveTrackColor = Warm.Track, activeTickColor = Warm.Brand, inactiveTickColor = Warm.Track,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(hoursLabel(hours), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
    }
}

internal fun hoursLabel(h: Float): String {
    val halves = kotlin.math.round(h * 2).toInt()
    return if (halves % 2 == 0) "${halves / 2} h" else "${halves / 2}.5 h"
}

// ---------- #32 KSS self-rating ----------

/** One self-rating: Karolinska Sleepiness Scale (1 = extremely alert ... 9 = fighting sleep) and our score then. */
data class KssSample(val kss: Int, val score: Int)

/** Pearson correlation between self-rated sleepiness and our fatigue score; null below 3 samples or no variance. */
internal fun correlation(samples: List<KssSample>): Double? {
    if (samples.size < 3) return null
    val xs = samples.map { it.kss.toDouble() }
    val ys = samples.map { it.score.toDouble() }
    val mx = xs.average(); val my = ys.average()
    val cov = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
    val vx = xs.sumOf { (it - mx) * (it - mx) }; val vy = ys.sumOf { (it - my) * (it - my) }
    if (vx == 0.0 || vy == 0.0) return null
    return cov / sqrt(vx * vy)
}

/** Ratings kept in memory for the session (local only, never sent). */
object KssLog {
    val samples = mutableStateListOf<KssSample>()
}

private val KSS_LABELS = mapOf(1 to "very alert", 3 to "alert", 5 to "neither", 7 to "sleepy", 9 to "fighting sleep")

/**
 * KSS pop-up (#32): every [everyMs] after calibration (30 min, 40 s in demo), a small card top right
 * asks "How sleepy do you feel?" 1..9; the answer is stored with our score at that moment.
 */
@Composable
fun BoxScope.KssPrompt(state: DashboardState, demo: Boolean) {
    val everyMs = if (demo) 40_000L else 30 * 60_000L
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(state.runId, state.fatigue.calibrated) {
        if (!state.fatigue.calibrated) return@LaunchedEffect
        while (true) {
            delay(everyMs)
            open = true
        }
    }
    AnimatedVisibility(
        open,
        Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 48.dp),
        enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        Column(
            Modifier.background(Warm.Surface, RoundedCornerShape(24.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How sleepy do you feel?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
                Spacer(Modifier.width(24.dp))
                Text("Skip", fontSize = 16.sp, color = Warm.Muted, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { open = false }.padding(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..9).forEach { k ->
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(lerpEnergy(k))
                            .clickable { KssLog.samples.add(KssSample(k, state.fatigue.score)); open = false },
                        contentAlignment = Alignment.Center,
                    ) { Text("$k", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Warm.Deep) }
                }
            }
            Row(Modifier.width(496.dp)) { // = 9 buttons + gaps, so the weight below can't stretch the card
                Text("1 very alert", fontSize = 14.sp, color = Warm.Muted)
                Spacer(Modifier.weight(1f))
                Text("9 fighting sleep", fontSize = 14.sp, color = Warm.Muted)
            }
        }
    }
}

/** KSS 1 (alert) .. 9 (sleepy) on our energy scale: honey .. red. */
private fun lerpEnergy(kss: Int) = energyColor(100 - (kss - 1) * 100 / 8)

/** One line for the dashboard: last rating vs our score, plus the correlation once there are 3 ratings. */
@Composable
fun KssSummary(modifier: Modifier = Modifier) {
    val last = KssLog.samples.lastOrNull() ?: return
    val r = correlation(KssLog.samples)
    val label = KSS_LABELS[last.kss]?.let { " ($it)" } ?: ""
    Text(
        "You said ${last.kss}/9$label · we measured ${last.score}" + (r?.let { " · match r = ${it.fmt(2)} (n=${KssLog.samples.size})" } ?: ""),
        fontSize = 16.sp, color = Warm.Muted, modifier = modifier,
    )
}
