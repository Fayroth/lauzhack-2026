package ch.lauzhack.energyguard.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
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
import ch.lauzhack.energyguard.ui.today.LiveSignalsCard
import ch.lauzhack.energyguard.ui.today.SignalsSnapshot
import ch.lauzhack.energyguard.ui.today.SleepHoursSlider
import ch.lauzhack.energyguard.ui.today.TodayActions
import ch.lauzhack.energyguard.ui.today.calibrationSecondsLeft
import ch.lauzhack.energyguard.ui.windowLabel

/**
 * Today on a phone (M3): the desktop Today's data in one scrolling column, same tokens and components.
 * Concentration (gauge, level, sentence, reasons) -> Energy -> Recovery -> Coach -> Live signals -> stats (2 columns).
 * Live from the PC it is read-only (no break button, no sleep slider: "Change on your PC"); in demo it is the
 * phone's own AppStore, so both work.
 */
@Composable
fun PhoneTodayScreen(
    state: AppState,
    signals: SignalsSnapshot,
    link: PhoneLink,
    actions: TodayActions,
    onReplay: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val readOnly = link is PhoneLink.Live
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EgSpace.s, vertical = EgSpace.xs),
        verticalArrangement = Arrangement.spacedBy(EgSpace.s),
    ) {
        when (link) {
            PhoneLink.NotPaired -> ConnectCard(onConnect)
            is PhoneLink.Connecting -> StatusCard("Connecting to your PC (${link.pc})…", Eg.colors.accent)
            is PhoneLink.Offline -> StatusCard("Can't reach your PC (${link.pc}). Retrying · demo data meanwhile.", Eg.colors.tired)
            is PhoneLink.Live -> Unit
        }
        notice?.let { StatusCard(it, Eg.colors.fresh) }
        ConcentrationCard(state)
        EnergyCard(state, actions, readOnly)
        RecoveryCard(state, actions, readOnly)
        CoachCard(state.coach, Modifier.fillMaxWidth(), format = ::factorize, maxLines = 6)
        LiveSignalsCard(signals, Modifier.fillMaxWidth(), stacked = true)
        StatsGrid(state)
        if (!readOnly) {
            TextButton(onClick = onReplay, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text("Replay the demo", style = EgType.bodyStrong, color = Eg.colors.accent)
            }
        }
        Spacer(Modifier.height(EgSpace.xs))
    }
}

/** Not paired yet: the one call to action, on top of the demo data. */
@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val c = Eg.colors
    EgCard(Modifier.fillMaxWidth()) {
        Text("Connect to your PC", style = EgType.title, color = c.text)
        Spacer(Modifier.height(EgSpace.xs))
        Text(
            "See your PC's live energy here: webcam, mouse and keyboard stay on the PC, only numbers come over Wi-Fi. Demo data until then.",
            style = EgType.body, color = c.muted,
        )
        Spacer(Modifier.height(EgSpace.s))
        EgButton("Connect", onConnect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusCard(text: String, color: Color) {
    val c = Eg.colors
    EgCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(text, style = EgType.body, color = c.text)
        }
    }
}

@Composable
private fun ConcentrationCard(state: AppState) {
    val c = Eg.colors
    val conc = state.concentration
    val secondsLeft = key(state.dashboard.runId) { calibrationSecondsLeft(state.dashboard.calibrationMs, conc.calibrated) }
    EgCard(Modifier.fillMaxWidth()) {
        CardTitle("Concentration") {
            if (conc.calibrated) Text("over the ${windowLabel(state.dashboard.windowMs)}", style = EgType.label, color = c.muted)
        }
        Spacer(Modifier.height(EgSpace.s))
        Gauge(
            conc.value, conc.level, Modifier.size(240.dp).align(Alignment.CenterHorizontally),
            calibrating = !conc.calibrated, calibrationSecondsLeft = secondsLeft,
        )
        Spacer(Modifier.height(EgSpace.s))
        Column(verticalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
            if (!conc.calibrated) {
                Text("Learning", style = EgType.value, color = c.text)
                Text("Your normal blinks, mouse and typing rhythm. Just work as usual.", style = EgType.lead, color = c.muted)
            } else {
                Text(levelLabel(conc.level), style = EgType.value, color = c.level(conc.level))
                Text(levelSentence(conc.level), style = EgType.lead, color = c.muted)
                Spacer(Modifier.height(EgSpace.xs))
                Text("WHY", style = EgType.label, color = c.muted)
                if (conc.reasons.isEmpty()) Text("Every signal is at your usual level.", style = EgType.body, color = c.text)
                else BulletList(conc.reasons.take(3).map(::plainReason), c.level(conc.level))
            }
        }
    }
}

@Composable
private fun EnergyCard(state: AppState, actions: TodayActions, readOnly: Boolean) {
    val c = Eg.colors
    val e = state.energy
    MetricCard(
        "Energy", "${e.battery}", Modifier.fillMaxWidth(), unit = "%",
        trailing = { if (e.onBreak) Pill("On break", c.accent, dot = true) },
        beside = {
            Spacer(Modifier.weight(1f))
            if (!readOnly) {
                if (e.onBreak) EgButton("Back to work", actions.onEndBreak, primary = false)
                else EgButton("I'm on a break", actions.onStartBreak)
            }
        },
    ) {
        Spacer(Modifier.height(EgSpace.xs))
        BatteryBar(e.battery)
        Spacer(Modifier.height(EgSpace.xs))
        Text(
            if (e.onBreak) "Recharging while you rest" else "Started at ${e.startFromSleep}% from last night's sleep",
            style = EgType.label, color = c.muted,
        )
    }
}

@Composable
private fun RecoveryCard(state: AppState, actions: TodayActions, readOnly: Boolean) {
    val c = Eg.colors
    val s = state.stats
    val checkedIn = state.journal.any { (it.kss != null || it.stress != null) && localDayKey(it.ts) == s.day }
    MetricCard("Recovery", "${state.recovery}", Modifier.fillMaxWidth(), unit = "/100") {
        Text(
            "Breaks ${s.breaksTaken}/${s.breaksNeeded} · " + if (checkedIn) "Check-in done" else "No check-in",
            style = EgType.body, color = c.muted,
        )
        Spacer(Modifier.height(EgSpace.xs))
        if (readOnly) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Last night", style = EgType.label, color = c.muted)
                Spacer(Modifier.width(EgSpace.s))
                Text(hoursText(state.settings.sleepHours), style = EgType.bodyStrong, color = c.text)
                Spacer(Modifier.weight(1f))
                Text("Change on your PC", style = EgType.label, color = c.muted)
            }
        } else {
            SleepHoursSlider(state.settings.sleepHours, actions.onSleepHours, height = 48.dp)
        }
    }
}

@Composable
private fun StatsGrid(state: AppState) {
    val c = Eg.colors
    val s = state.stats
    StatRow {
        StatTile(durationParts(s.focusedMs), "focused today", it, labelMaxLines = 2)
        StatTile("${s.breaksTaken}/${s.breaksNeeded}", "breaks / needed", it, labelMaxLines = 2)
    }
    StatRow {
        StatTile(durationParts(s.longestFocusMs), "longest focus", it, labelMaxLines = 2)
        StatTile("${s.phishingBlocked}", "phishing blocked", it, labelMaxLines = 2)
    }
    StatRow {
        StatTile("${s.llmCalls}", "LLM calls today", it, valueColor = c.accent, labelMaxLines = 2)
        // Always 0 by design; one number reads stronger than three.
        StatTile("${s.imagesSent + s.audioSent + s.keysSent}", "images · audio · keys sent", it, labelMaxLines = 2)
    }
}

/** Two equal tiles side by side, same height. */
@Composable
private fun StatRow(tiles: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
        tiles(Modifier.weight(1f).fillMaxHeight())
    }
}
