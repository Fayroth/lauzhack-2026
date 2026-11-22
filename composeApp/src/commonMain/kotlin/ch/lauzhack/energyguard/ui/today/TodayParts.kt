package ch.lauzhack.energyguard.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.EnergyHistory
import ch.lauzhack.energyguard.app.localClockTime
import ch.lauzhack.energyguard.ui.components.AreaChart
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.GlowDot
import ch.lauzhack.energyguard.ui.components.LevelChip
import ch.lauzhack.energyguard.ui.components.QuietStat
import ch.lauzhack.energyguard.ui.components.Ring
import ch.lauzhack.energyguard.ui.components.clockLabel
import ch.lauzhack.energyguard.ui.components.durationShort
import ch.lauzhack.energyguard.ui.components.hoursText
import ch.lauzhack.energyguard.ui.components.levelSentence
import ch.lauzhack.energyguard.ui.components.shortReason
import ch.lauzhack.energyguard.ui.components.windowShort
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wall clock, recomposed every [everyMs] (header time, chart "Now"). */
@OptIn(ExperimentalTime::class)
@Composable
internal fun rememberNow(everyMs: Long = 15_000): Long {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(everyMs) {
        while (true) {
            delay(everyMs)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    return now
}

/**
 * The three equal rings: Energy (lime), Concentration (state colour; "Focus" on a phone), Recovery (ice), each with
 * a coloured dot + name under it and, when [captions], one line of context.
 */
@Composable
internal fun RingsRow(
    state: AppState,
    ringSize: Dp,
    modifier: Modifier = Modifier,
    captions: Boolean = true,
    concentrationName: String = "Concentration",
    stroke: Dp = 14.dp,
    numberStyle: TextStyle = EgType.gauge,
    nameStyle: TextStyle = EgType.ringName,
    unitStyle: TextStyle = EgType.label,
) {
    val c = Eg.colors
    val e = state.energy
    val conc = state.concentration
    val s = state.stats
    val secondsLeft = calibrationLeft(state)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        RingColumn(
            "Energy", c.accent, ringSize, captions,
            caption = if (e.onBreak) "on a break · recharging" else "from ${e.startFromSleep}% · ${hoursText(state.settings.sleepHours)} of sleep",
            nameStyle = nameStyle,
        ) { Ring(e.battery, c.accent, "%", Modifier.size(ringSize), stroke = stroke, numberStyle = numberStyle, unitStyle = unitStyle) }
        val concColor = if (conc.calibrated) c.level(conc.level) else c.muted
        RingColumn(
            concentrationName, concColor, ringSize, captions,
            caption = if (conc.calibrated) windowShort(state.dashboard.windowMs) else "getting to know you",
            nameStyle = nameStyle,
        ) {
            if (conc.calibrated) {
                Ring(conc.value, concColor, "score", Modifier.size(ringSize), stroke = stroke, numberStyle = numberStyle, unitStyle = unitStyle)
            } else {
                Ring(0, concColor, "learning", Modifier.size(ringSize), number = "${secondsLeft}s", stroke = stroke, numberStyle = numberStyle, unitStyle = unitStyle)
            }
        }
        RingColumn(
            "Recovery", c.ice, ringSize, captions,
            caption = "sleep ${hoursText(state.settings.sleepHours)} · breaks ${s.breaksTaken} of ${s.breaksNeeded}",
            nameStyle = nameStyle,
        ) { Ring(state.recovery, c.ice, "/100", Modifier.size(ringSize), stroke = stroke, numberStyle = numberStyle, unitStyle = unitStyle) }
    }
}

@Composable
private fun RingColumn(
    name: String,
    color: Color,
    ringSize: Dp,
    captions: Boolean,
    caption: String,
    nameStyle: TextStyle,
    ring: @Composable () -> Unit,
) {
    val c = Eg.colors
    // Captions need room beside the ring (desktop); without them (phone) three columns fit a 340 dp card.
    Column(Modifier.width(ringSize + if (captions) 48.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ring()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowDot(color, 8.dp, glow = false)
            Spacer(Modifier.width(8.dp))
            Text(name, style = nameStyle, color = c.text, maxLines = 1)
        }
        if (captions) {
            Spacer(Modifier.height(2.dp))
            Text(caption, style = EgType.small, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** Seconds left in the current engine's calibration (per run: F5 / Recalibrate restart it). */
@Composable
internal fun calibrationLeft(state: AppState): Int = key(state.dashboard.runId) {
    calibrationSecondsLeft(state.dashboard.calibrationMs, state.concentration.calibrated, state.dashboard.calibrationStartMs)
}

/** State chip + sentence + reasons inline ("4.5× eyes closing · 1.8× longer blinks"), numbers in the state colour. */
@Composable
internal fun StateLine(state: AppState, modifier: Modifier = Modifier, maxLines: Int = 2, stacked: Boolean = false) {
    val c = Eg.colors
    val conc = state.concentration
    val secondsLeft = if (conc.calibrated) null else calibrationLeft(state)
    val text = buildAnnotatedString {
        if (!conc.calibrated) {
            withStyle(SpanStyle(color = c.text)) { append("Learning your normal blinks, mouse and typing rhythm. ") }
            withStyle(SpanStyle(color = c.muted)) { append("Just work as usual.") }
            return@buildAnnotatedString
        }
        withStyle(SpanStyle(color = c.text)) { append(levelSentence(conc.level)) }
        append("  ")
        if (conc.reasons.isEmpty()) {
            withStyle(SpanStyle(color = c.muted)) { append("Every signal is at your usual level.") }
        } else {
            conc.reasons.take(3).map(::shortReason).forEachIndexed { i, r ->
                if (i > 0) withStyle(SpanStyle(color = c.label)) { append("  ·  ") }
                if (r.amount.isNotEmpty()) {
                    withStyle(SpanStyle(color = c.level(conc.level), fontWeight = FontWeight.SemiBold)) { append(r.amount) }
                    append(" ")
                }
                withStyle(SpanStyle(color = c.muted)) { append(r.what) }
            }
        }
    }
    if (stacked) {
        Column(modifier) {
            LevelChip(conc.level, calibrating = !conc.calibrated, secondsLeft = secondsLeft)
            Spacer(Modifier.height(12.dp))
            Text(text, style = EgType.body, maxLines = maxLines + 2, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            LevelChip(conc.level, calibrating = !conc.calibrated, secondsLeft = secondsLeft)
            Spacer(Modifier.width(16.dp))
            Text(text, style = EgType.body, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Take a 5-min break" (lime) or "Back to work" while on a break. */
@Composable
internal fun BreakButton(state: AppState, actions: TodayActions, modifier: Modifier = Modifier) {
    if (state.energy.onBreak) EgButton("Back to work", actions.onEndBreak, modifier)
    else EgButton("Take a 5-min break", actions.onStartBreak, modifier)
}

/** Energy through the day: lime line + fading area, x labels on round hours up to "Now" ([chartTicks]). */
@Composable
internal fun EnergyChartCard(state: AppState, modifier: Modifier = Modifier, chartHeight: Dp? = null) {
    val c = Eg.colors
    val values = state.energyHistory.values.ifEmpty { listOf(state.energy.battery) }
    val now = rememberNow()
    EgCard(modifier) {
        CardTitle("Energy through the day") {
            Text("${state.energy.battery}%", style = EgType.stat, color = c.text)
        }
        Spacer(Modifier.height(14.dp))
        val chart = if (chartHeight != null) Modifier.fillMaxWidth().height(chartHeight) else Modifier.fillMaxWidth().weight(1f)
        AreaChart(values, c.accent, chart)
        Spacer(Modifier.height(10.dp))
        val h = state.energyHistory
        val startMin = if (state.demo) 8 * 60 else {
            val startMs = h.startMs.takeIf { it > 0 } ?: (now - (values.size - 1) * EnergyHistory.STEP_MS)
            minuteOfDay(localClockTime(startMs))
        }
        ChartAxis(chartTicks(startMin, startMin + (values.size - 1) * EnergyHistory.STEP_MIN))
    }
}

/** "14:35" -> 875. */
private fun minuteOfDay(hhmm: String): Int =
    hhmm.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

/**
 * X labels of the energy chart on round hours (pure, unit-tested): (position 0..1, "08:00") between [startMin] and
 * [endMin] (minutes of the day; demo samples are anchored at 08:00). The step is the largest of 4 h / 2 h / 1 h /
 * 30 / 15 min that still gives 2 labels (full day: 08:00, 12:00, 16:00, 20:00). "Now" is not included: [ChartAxis]
 * puts it at the right end and drops any hour label that would overlap it.
 */
fun chartTicks(startMin: Int, endMin: Int): List<Pair<Float, String>> {
    val span = endMin - startMin
    if (span <= 0) return emptyList()
    fun ticks(step: Int): List<Pair<Float, String>> {
        val first = (startMin + step - 1) / step * step
        return (first..endMin step step)
            .map { (it - startMin).toFloat() / span to clockLabel(it % (24 * 60)) }
    }
    val steps = listOf(240, 120, 60, 30, 15)
    return steps.map(::ticks).firstOrNull { it.size >= 2 } ?: steps.map(::ticks).firstOrNull { it.isNotEmpty() } ?: emptyList()
}

/** Hour labels at their time position, "Now" at the right end; a label that would overlap another is dropped. */
@Composable
private fun ChartAxis(ticks: List<Pair<Float, String>>) {
    val c = Eg.colors
    Layout(
        content = {
            Text("Now", style = EgType.small, color = c.label, maxLines = 1)
            ticks.forEach { Text(it.second, style = EgType.small, color = c.label, maxLines = 1) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            val nowLabel = placeables[0]
            val nowX = width - nowLabel.width
            nowLabel.place(nowX, 0)
            var lastRight = Int.MIN_VALUE
            val gap = 12.dp.roundToPx()
            placeables.drop(1).forEachIndexed { i, p ->
                val x = (ticks[i].first * width - p.width / 2f).toInt().coerceIn(0, width - p.width)
                if (x >= lastRight + gap && x + p.width <= nowX - gap) {
                    p.place(x, 0)
                    lastRight = x + p.width
                }
            }
        }
    }
}

/** The quiet stats (one line on desktop, 2-column grid on a phone): numbers white, labels secondary. */
internal fun quietStats(state: AppState): List<QuietStat> {
    val s = state.stats
    return listOf(
        QuietStat(durationShort(s.focusedMs), "focused"),
        QuietStat("${s.breaksTaken} / ${s.breaksNeeded}", "breaks"),
        QuietStat(durationShort(s.longestFocusMs), "longest focus"),
        QuietStat("${s.phishingBlocked}", "phishing blocked"),
        QuietStat("${s.llmCalls}", if (s.llmCalls == 1) "AI call" else "AI calls"),
        // Always 0 by design; one number reads stronger than three.
        QuietStat("${s.imagesSent + s.audioSent + s.keysSent}", "images · audio · keys sent"),
    )
}

/** "AI · 23:38": when the shown coach note arrived (local time), null when unknown. */
internal fun coachTime(state: AppState): String? = state.coach?.ts?.let(::localClockTime)
