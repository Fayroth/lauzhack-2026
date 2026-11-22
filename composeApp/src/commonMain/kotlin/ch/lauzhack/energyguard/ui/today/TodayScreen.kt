package ch.lauzhack.energyguard.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.Settings
import ch.lauzhack.energyguard.app.localClockTime
import ch.lauzhack.energyguard.ui.components.CoachCard
import ch.lauzhack.energyguard.ui.components.Divider
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.QuietStatText
import ch.lauzhack.energyguard.ui.components.dayLabel
import ch.lauzhack.energyguard.ui.components.hoursText
import ch.lauzhack.energyguard.ui.factorize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/** User actions of the Today tab (and the sleep slider, now on Profile), wired to AppStore by the shell. */
class TodayActions(
    val onStartBreak: () -> Unit = {},
    val onEndBreak: () -> Unit = {},
    val onSleepHours: (Double) -> Unit = {},
)

/**
 * Tab 1 "Today" on the desktop (C2), driven only by [AppState]; fits 1440x860 without scrolling.
 * Hero card (three equal rings, state line, break button), a row of three equal cards (coach, energy through the day,
 * live signals), then the stats as one quiet line.
 */
@Composable
fun TodayScreen(state: AppState, actions: TodayActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
        HeroCard(state, actions, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            val card = Modifier.weight(1f).fillMaxHeight()
            CoachCard(state.coach, card, format = ::factorize, maxLines = 3, time = coachTime(state))
            EnergyChartCard(state, card)
            LiveSignalsCard(state.liveSignals(), card, state.concentration.level)
        }
        QuietStatsLine(state)
    }
}

@Composable
private fun HeroCard(state: AppState, actions: TodayActions, modifier: Modifier) {
    val c = Eg.colors
    val now = rememberNow()
    EgCard(modifier, hero = true, padding = 28.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = EgType.screenTitle, color = c.text)
            Spacer(Modifier.weight(1f))
            Text("${dayLabel(state.stats.day)}  ·  ${localClockTime(now)}", style = EgType.body, color = c.muted)
        }
        Spacer(Modifier.height(EgSpace.s))
        RingsRow(state, ringSize = 176.dp)
        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StateLine(state, Modifier.weight(1f))
            Spacer(Modifier.width(EgSpace.m))
            BreakButton(state, actions)
        }
    }
}

/** "1h 12m focused · 1 / 1 breaks · ..." on one line, no boxes. */
@Composable
private fun QuietStatsLine(state: AppState) {
    val c = Eg.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
        quietStats(state).forEachIndexed { i, s ->
            if (i > 0) Text("  ·  ", style = EgType.label, color = c.label, modifier = Modifier.padding(bottom = 2.dp))
            QuietStatText(s)
        }
    }
}

/**
 * "Last night [slider] 6.5 h": drives the battery start and the recovery score ([onDone] on release, by half hours).
 * [height] = touch height of the slider (>= 44 dp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepHoursSlider(sleepHours: Double, onDone: (Double) -> Unit, height: Dp = 44.dp) {
    val c = Eg.colors
    var hours by remember(sleepHours) { mutableFloatStateOf(sleepHours.toFloat()) }
    val range = Settings.MIN_SLEEP_HOURS.toFloat()..Settings.MAX_SLEEP_HOURS.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Last night", style = EgType.label, color = c.muted)
        Spacer(Modifier.width(EgSpace.s))
        // Own thumb + track: M3's default draws a bar thumb, a gap and a stop dot at the end.
        Slider(
            value = hours,
            onValueChange = { hours = (it * 2).roundToInt() / 2f },
            onValueChangeFinished = { onDone(hours.toDouble()) },
            valueRange = range,
            modifier = Modifier.weight(1f).height(height),
            thumb = { Box(Modifier.size(22.dp).background(c.accent, CircleShape)) },
            track = { SleepTrack((hours - range.start) / (range.endInclusive - range.start)) },
        )
        Spacer(Modifier.width(EgSpace.s))
        Text(
            hoursText(hours.toDouble()), style = EgType.bodyStrong, color = c.text,
            textAlign = TextAlign.End, modifier = Modifier.width(56.dp),
        )
    }
}

/** 6 dp rounded track: lime up to [fraction], track colour after. */
@Composable
private fun SleepTrack(fraction: Float) {
    val c = Eg.colors
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.track, cornerRadius = r)
        drawRoundRect(c.accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/**
 * Seconds left in calibration: from [startedMs] (wall clock the engine started, e.g. after "Recalibrate" on Profile)
 * when known, else counted from when it is first shown (keyed on the run by the caller).
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun calibrationSecondsLeft(calibrationMs: Long, calibrated: Boolean, startedMs: Long = 0): Int {
    val total = (calibrationMs / 1000).toInt()
    val start = remember { TimeSource.Monotonic.markNow() }
    fun elapsedS(): Int =
        if (startedMs > 0) ((Clock.System.now().toEpochMilliseconds() - startedMs) / 1000).toInt()
        else start.elapsedNow().inWholeSeconds.toInt()
    var left by remember { mutableIntStateOf((total - elapsedS()).coerceIn(0, total)) }
    LaunchedEffect(start, calibrated, startedMs) {
        while (!calibrated) {
            left = (total - elapsedS()).coerceIn(0, total)
            delay(250)
        }
    }
    return left
}
