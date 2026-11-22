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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.ThemeMode
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
import ch.lauzhack.energyguard.ui.today.SleepHoursSlider
import kotlin.math.roundToInt

/**
 * Tab 3 "Profile" on the desktop (C2): last night's sleep (the slider moved here from Today), the personal baseline,
 * the phone pairing line ([pairing], null when the companion is off) and the theme. Two columns, no scrolling.
 */
@Composable
fun ProfileScreen(
    state: AppState,
    onSleepHours: (Double) -> Unit,
    onToggleTheme: () -> Unit,
    pairing: String?,
    modifier: Modifier = Modifier,
) {
    val c = Eg.colors
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(EgSpace.s)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            EgCard(Modifier.fillMaxWidth(), hero = true, padding = 28.dp) {
                Text("Profile", style = EgType.screenTitle, color = c.text)
                Spacer(Modifier.height(EgSpace.xs))
                Text("What normal looks like for you, and how the app runs.", style = EgType.body, color = c.muted)
                Spacer(Modifier.height(EgSpace.m))
                Text("Sleep last night", style = EgType.title, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text("Sets where today's energy starts and 50 % of your recovery.", style = EgType.label, color = c.muted)
                Spacer(Modifier.height(EgSpace.xs))
                SleepHoursSlider(state.settings.sleepHours, onSleepHours)
            }
            BaselineCard(state.profile.baseline, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EgSpace.s)) {
            EgCard(Modifier.fillMaxWidth()) {
                CardTitle("Your phone") { if (pairing != null) Pill("Sharing on", color = c.accent) }
                Spacer(Modifier.height(EgSpace.xs))
                if (pairing != null) {
                    Text("Same Wi-Fi as this PC. In the phone app, Profile → Connect, then type:", style = EgType.body, color = c.muted)
                    Spacer(Modifier.height(EgSpace.s))
                    Text(pairing.removePrefix("Phone: "), style = EgType.heading, color = c.text)
                } else {
                    Text(
                        "Phone sharing is off. Start EnergyGuard with EG_COMPANION=1 to show live numbers on your phone.",
                        style = EgType.body, color = c.muted,
                    )
                }
            }
            EgCard(Modifier.fillMaxWidth()) {
                CardTitle("Privacy")
                Spacer(Modifier.height(EgSpace.xs))
                Text(
                    "Webcam, mouse and keyboard are processed on this PC. No image or audio is stored or sent, no key is " +
                        "recorded (timings and counts only). The AI coach gets a few numbers, at most once per 10 minutes.",
                    style = EgType.body, color = c.text,
                )
                Spacer(Modifier.height(EgSpace.s))
                LockedNote("Journal notes stay on this device.")
            }
            EgCard(Modifier.fillMaxWidth()) {
                CardTitle("Appearance")
                Spacer(Modifier.height(EgSpace.s))
                EgButton(
                    if (state.settings.theme == ThemeMode.LIGHT) "Switch to dark mode" else "Switch to light mode",
                    onToggleTheme, primary = false,
                )
            }
        }
    }
}

/** The personal baseline learnt in the first minute, as plain numbers. */
@Composable
private fun BaselineCard(b: BaselineSummary, modifier: Modifier) {
    val c = Eg.colors
    EgCard(modifier) {
        CardTitle("Your baseline") { Pill(if (b.calibrated) "Learnt" else "Learning", color = if (b.calibrated) c.ice else null) }
        Spacer(Modifier.height(EgSpace.xs))
        Text("Every score compares you with yourself, not with an average person.", style = EgType.label, color = c.muted)
        Spacer(Modifier.height(EgSpace.s))
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
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = EgType.body, color = c.muted, modifier = Modifier.weight(1f))
                Text(value ?: "–", style = EgType.bodyStrong, color = if (value != null) c.text else c.label)
            }
        }
    }
}
