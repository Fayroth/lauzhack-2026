package ch.lauzhack.energyguard.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.ui.components.CardTitle
import ch.lauzhack.energyguard.ui.components.Divider
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.LockedNote
import ch.lauzhack.energyguard.ui.components.Pill
import ch.lauzhack.energyguard.ui.components.EgSwitch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import ch.lauzhack.energyguard.ui.today.SleepHoursSlider
import kotlin.math.roundToInt

/** Desktop Profile actions, wired to AppStore / the companion server / the engine by main.kt. */
class ProfileActions(
    val onSleepHours: (Double) -> Unit = {},
    val settings: SettingsActions = SettingsActions(),
    val onSharing: (Boolean) -> Unit = {},
    val onRecalibrate: () -> Unit = {},
)

/**
 * Phone sharing (companion server) as the "Your phone" card shows it: [on] = the switch, [pairing] = "IP:port · code"
 * while listening (null when off, or on but the port could not be opened).
 */
data class Sharing(val on: Boolean, val pairing: String? = null)

/**
 * Tab 3 "Profile" on the desktop (C2, M5). Left: sleep last night, the personal baseline (+ Recalibrate), privacy.
 * Right: last 7 days, settings, phone sharing. Two columns, no scrolling at 1440x860.
 * [coachUnavailable] = why the AI coach can't go online ("no key"), null if it can.
 */
@Composable
fun ProfileScreen(
    state: AppState,
    actions: ProfileActions,
    sharing: Sharing,
    coachUnavailable: String?,
    modifier: Modifier = Modifier,
) {
    val c = Eg.colors
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            EgCard(Modifier.fillMaxWidth(), hero = true) {
                Text("Profile", style = EgType.screenTitle, color = c.text)
                Spacer(Modifier.height(EgSpace.s))
                Text("Sleep last night", style = EgType.title, color = c.text)
                Text("Sets where today's energy starts and 50 % of your recovery.", style = EgType.label, color = c.muted)
                SleepHoursSlider(state.settings.sleepHours, actions.onSleepHours)
            }
            BaselineCard(state.profile.baseline, state.concentration.calibrated, actions.onRecalibrate, Modifier.fillMaxWidth())
            EgCard(Modifier.fillMaxWidth().weight(1f)) {
                CardTitle("Privacy")
                Spacer(Modifier.height(EgSpace.xs))
                Text(
                    "Webcam, mouse and keyboard are processed on this PC. No image or audio is stored or sent, no key is " +
                        "recorded (timings and counts only). The AI coach gets a few numbers, at most once per 10 minutes.",
                    style = EgType.label, color = c.text,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(EgSpace.xs))
                LockedNote("Journal notes stay on this device.")
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            Last7DaysCard(state, Modifier.fillMaxWidth(), chartHeight = 104.dp)
            SettingsCard(state.settings, actions.settings, coachUnavailable, hapticsWhat = "Mouse", modifier = Modifier.fillMaxWidth())
            PhoneSharingCard(sharing, actions.onSharing, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** "Your phone": title row = the "Phone sharing" switch (whole row toggles), then the address + code, or "Off". */
@Composable
private fun PhoneSharingCard(sharing: Sharing, onSharing: (Boolean) -> Unit, modifier: Modifier) {
    val c = Eg.colors
    EgCard(modifier) {
        Row(
            Modifier.fillMaxWidth().height(EgSpace.touch).toggleable(sharing.on, role = Role.Switch, onValueChange = onSharing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your phone", style = EgType.title, color = c.text)
            Spacer(Modifier.weight(1f))
            Text(if (sharing.on) "Phone sharing on" else "Phone sharing", style = EgType.label, color = if (sharing.on) c.text else c.muted)
            Spacer(Modifier.width(12.dp))
            EgSwitch(sharing.on)
        }
        Spacer(Modifier.height(4.dp))
        when {
            !sharing.on -> Text(
                "Off. Turn it on to see your live numbers on your phone (same Wi-Fi, read-only, with a code).",
                style = EgType.label, color = c.muted,
            )
            sharing.pairing == null -> Text("Could not open the port: another app may be using it.", style = EgType.label, color = c.exhausted)
            else -> {
                Text("Same Wi-Fi. In the phone app, Profile → Connect, then type:", style = EgType.label, color = c.muted)
                Spacer(Modifier.height(4.dp))
                Text(sharing.pairing.removePrefix("Phone: "), style = EgType.heading, color = c.text, maxLines = 1)
            }
        }
    }
}

/**
 * The personal baseline learnt in the first minute, as plain numbers. "Recalibrate" restarts the learning (fresh
 * engine: 15 s in demo, 60 s live), disabled while it is already learning.
 */
@Composable
private fun BaselineCard(b: BaselineSummary, calibrated: Boolean, onRecalibrate: () -> Unit, modifier: Modifier) {
    val c = Eg.colors
    EgCard(modifier) {
        CardTitle("Your baseline") {
            Pill(if (b.calibrated) "Learnt" else "Learning", color = if (b.calibrated) c.ice else null)
            Spacer(Modifier.width(EgSpace.xs))
            EgButton("Recalibrate", onRecalibrate, primary = false, enabled = calibrated)
        }
        Text("Every score compares you with yourself, not with an average person.", style = EgType.label, color = c.muted)
        Spacer(Modifier.height(EgSpace.xs))
        val rows = listOf(
            "Blink rate" to b.blinkRatePerMin?.let { "${it.roundToInt()} /min" },
            "Blink duration" to b.blinkMs?.let { "${it.roundToInt()} ms" },
            "Eyes closed (PERCLOS)" to b.perclos?.let { "${(it * 100).roundToInt()} %" },
            "Mouse jitter" to b.mouseJitter?.let { "${(it * 10).roundToInt() / 10.0} /s" },
            "Typing rhythm" to b.interKeyMs?.let { "${it.roundToInt()} ms/key" },
            "Backspace rate" to b.backspaceRatio?.let { "${(it * 100).roundToInt()} %" },
        )
        rows.forEachIndexed { i, (name, value) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = EgType.body, color = c.muted, modifier = Modifier.weight(1f))
                Text(value ?: "–", style = EgType.bodyStrong, color = if (value != null) c.text else c.label)
            }
        }
    }
}
