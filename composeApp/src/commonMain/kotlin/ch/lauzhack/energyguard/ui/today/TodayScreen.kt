package ch.lauzhack.energyguard.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.Settings
import ch.lauzhack.energyguard.app.localDayKey
import ch.lauzhack.energyguard.ui.components.BatteryBar
import ch.lauzhack.energyguard.ui.components.BulletList
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.CoachCard
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.Gauge
import ch.lauzhack.energyguard.ui.components.MetricCard
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.components.StatTile
import ch.lauzhack.energyguard.ui.components.durationParts
import ch.lauzhack.energyguard.ui.components.hoursText
import ch.lauzhack.energyguard.ui.components.levelLabel
import ch.lauzhack.energyguard.ui.components.levelSentence
import ch.lauzhack.energyguard.ui.components.plainReason
import ch.lauzhack.energyguard.ui.factorize
import ch.lauzhack.energyguard.ui.windowLabel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** User actions of the Today tab, wired to AppStore by the shell. */
class TodayActions(
    val onStartBreak: () -> Unit = {},
    val onEndBreak: () -> Unit = {},
    val onSleepHours: (Double) -> Unit = {},
)

/**
 * Tab 1 "Today", driven only by [AppState] (shared: desktop M2, Android M3).
 * Hero row (concentration | energy + recovery), coach card, 6 stat tiles.
 */
@Composable
fun TodayScreen(state: AppState, actions: TodayActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            ConcentrationCard(state, Modifier.weight(0.58f).fillMaxHeight())
            Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
                EnergyCard(state, actions, Modifier.fillMaxWidth().weight(1f))
                RecoveryCard(state, actions, Modifier.fillMaxWidth().weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            CoachCard(state.coach, Modifier.weight(0.55f).fillMaxHeight(), format = ::factorize)
            LiveSignalsCard(
                state.dashboard.vision, state.dashboard.input, state.profile.baseline, state.demo,
                Modifier.weight(0.45f).fillMaxHeight(),
            )
        }
        StatsRow(state)
    }
}

@Composable
private fun ConcentrationCard(state: AppState, modifier: Modifier) {
    val c = Eg.colors
    val conc = state.concentration
    val secondsLeft = key(state.dashboard.runId) { calibrationSecondsLeft(state.dashboard.calibrationMs, conc.calibrated) }
    EgCard(modifier) {
        CardTitle("Concentration") {
            if (conc.calibrated) Text("over the ${windowLabel(state.dashboard.windowMs)}", style = EgType.label, color = c.muted)
        }
        Spacer(Modifier.height(EgSpace.s))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Gauge(
                conc.value, conc.level,
                Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                calibrating = !conc.calibrated, calibrationSecondsLeft = secondsLeft,
            )
            Spacer(Modifier.width(EgSpace.l))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
                if (!conc.calibrated) {
                    Text("Learning", style = EgType.value, color = c.text)
                    Text("Your normal blinks, mouse and typing rhythm. Just work as usual.", style = EgType.lead, color = c.muted)
                } else {
                    Text(levelLabel(conc.level), style = EgType.value, color = c.level(conc.level))
                    Text(levelSentence(conc.level), style = EgType.lead, color = c.muted)
                    Spacer(Modifier.height(EgSpace.xs))
                    Text("WHY", style = EgType.label, color = c.muted)
                    if (conc.reasons.isEmpty()) {
                        Text("Every signal is at your usual level.", style = EgType.body, color = c.text)
                    } else {
                        BulletList(conc.reasons.take(3).map(::plainReason), c.level(conc.level))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyCard(state: AppState, actions: TodayActions, modifier: Modifier) {
    val c = Eg.colors
    val e = state.energy
    MetricCard(
        "Energy", "${e.battery}", modifier, unit = "%",
        trailing = { if (e.onBreak) Pill("On break", c.accent, dot = true) },
        beside = {
            Spacer(Modifier.weight(1f))
            if (e.onBreak) EgButton("Back to work", actions.onEndBreak, primary = false)
            else EgButton("I'm on a break", actions.onStartBreak)
        },
    ) {
        Spacer(Modifier.weight(1f))
        BatteryBar(e.battery)
        Spacer(Modifier.height(EgSpace.xs))
        Text(
            if (e.onBreak) "Recharging while you rest" else "Started at ${e.startFromSleep}% from last night's sleep",
            style = EgType.label, color = c.muted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryCard(state: AppState, actions: TodayActions, modifier: Modifier) {
    val c = Eg.colors
    val s = state.stats
    val checkedIn = state.journal.any { (it.kss != null || it.stress != null) && localDayKey(it.ts) == s.day }
    MetricCard(
        "Recovery", "${state.recovery}", modifier, unit = "/100",
        beside = {
            Spacer(Modifier.width(EgSpace.m))
            Text(
                "Sleep ${hoursText(state.settings.sleepHours)} · Breaks ${s.breaksTaken}/${s.breaksNeeded} · " +
                    if (checkedIn) "Check-in done" else "No check-in",
                style = EgType.body, color = c.muted, modifier = Modifier.weight(1f),
            )
        },
    ) {
        Spacer(Modifier.weight(1f))
        // Last night's sleep: drives the battery start and the recovery score (store.setSleepHours on release).
        var hours by remember(state.settings.sleepHours) { mutableFloatStateOf(state.settings.sleepHours.toFloat()) }
        val range = Settings.MIN_SLEEP_HOURS.toFloat()..Settings.MAX_SLEEP_HOURS.toFloat()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last night", style = EgType.label, color = c.muted)
            Spacer(Modifier.width(EgSpace.s))
            // Own thumb + track: M3's default draws a bar thumb, a gap and a stop dot at the end.
            Slider(
                value = hours,
                onValueChange = { hours = (it * 2).roundToInt() / 2f },
                onValueChangeFinished = { actions.onSleepHours(hours.toDouble()) },
                valueRange = range,
                modifier = Modifier.weight(1f).height(32.dp),
                thumb = { Box(Modifier.size(20.dp).shadow(2.dp, CircleShape).background(c.accent, CircleShape)) },
                track = { SleepTrack((hours - range.start) / (range.endInclusive - range.start)) },
            )
            Spacer(Modifier.width(EgSpace.s))
            Text(
                hoursText(hours.toDouble()), style = EgType.bodyStrong, color = c.text,
                textAlign = TextAlign.End, modifier = Modifier.width(48.dp),
            )
        }
    }
}

/** 8 dp rounded track: accent up to [fraction], track colour after. No ticks, no end dot. */
@Composable
private fun SleepTrack(fraction: Float) {
    val c = Eg.colors
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.track, cornerRadius = r)
        drawRoundRect(c.accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

@Composable
private fun StatsRow(state: AppState) {
    val c = Eg.colors
    val s = state.stats
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
        val tile = Modifier.weight(1f)
        StatTile(durationParts(s.focusedMs), "focused today", tile)
        StatTile("${s.breaksTaken}/${s.breaksNeeded}", "breaks / needed", tile)
        StatTile(durationParts(s.longestFocusMs), "longest focus", tile)
        StatTile("${s.phishingBlocked}", "phishing blocked", tile)
        StatTile("${s.llmCalls}", "LLM calls today", tile, valueColor = c.accent)
        // Always 0 by design; one number reads stronger than three.
        StatTile("${s.imagesSent + s.audioSent + s.keysSent}", "images · audio · keys sent", Modifier.weight(1.3f))
    }
}

/** Seconds left in calibration, counted from when it is first shown (keyed on the run by the caller). */
@Composable
private fun calibrationSecondsLeft(calibrationMs: Long, calibrated: Boolean): Int {
    val total = (calibrationMs / 1000).toInt()
    val start = remember { TimeSource.Monotonic.markNow() }
    var left by remember { mutableIntStateOf(total) }
    LaunchedEffect(start, calibrated) {
        while (!calibrated) {
            left = (total - start.elapsedNow().inWholeSeconds.toInt()).coerceAtLeast(0)
            delay(250)
        }
    }
    return left
}
