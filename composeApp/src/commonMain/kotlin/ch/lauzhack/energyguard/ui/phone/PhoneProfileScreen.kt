package ch.lauzhack.energyguard.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import ch.lauzhack.energyguard.ui.components.GlowDot
import ch.lauzhack.energyguard.ui.components.hoursText
import ch.lauzhack.energyguard.ui.today.SleepHoursSlider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.ui.profile.Last7DaysCard
import ch.lauzhack.energyguard.ui.profile.SettingsActions
import ch.lauzhack.energyguard.ui.profile.SettingsCard
import ch.lauzhack.energyguard.companion.PairingTarget
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.tracksTextFocus

/**
 * Profile tab on the phone (M3, M5): the PC pairing (status, "Change PC", the IP / port / code form), sleep, last 7 days,
 * settings (haptics, AI coach: always offline templates on the phone, light mode) and privacy. [state] = what is shown
 * (its settings are the phone's own, see CompanionState.toAppState).
 * The pairing itself is saved by the Android shell (private prefs); this screen only validates through
 * [PairingTarget.parse], so the URL is never built from raw text.
 */
@Composable
fun PhoneProfileScreen(
    link: PhoneLink,
    savedHost: String,
    savedPort: Int,
    error: String?,
    state: AppState,
    onConnect: (PairingTarget) -> Unit,
    settingsActions: SettingsActions,
    modifier: Modifier = Modifier,
    sleepHours: Double? = null,
    onSleepHours: ((Double) -> Unit)? = null,
) {
    val c = Eg.colors
    var editing by remember { mutableStateOf(false) }
    val showForm = editing || link == PhoneLink.NotPaired || error != null
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EgSpace.s, vertical = EgSpace.xs),
        verticalArrangement = Arrangement.spacedBy(EgSpace.s),
    ) {
        EgCard(Modifier.fillMaxWidth()) {
            Text("Your PC", style = EgType.title, color = c.text)
            Spacer(Modifier.height(EgSpace.xs))
            val (status, color) = linkStatus(link, error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowDot(color, 8.dp)
                Spacer(Modifier.width(12.dp))
                Text(status, style = EgType.bodyStrong, color = c.text)
            }
            if (!showForm) {
                Spacer(Modifier.height(EgSpace.s))
                EgButton("Change PC", { editing = true }, Modifier.fillMaxWidth(), primary = false)
            }
        }
        if (showForm) {
            PairingForm(savedHost, savedPort, canCancel = editing && link != PhoneLink.NotPaired, onCancel = { editing = false }) {
                editing = false
                onConnect(it)
            }
        }
        if (sleepHours != null) {
            // Moved here from Today (C2). Live from the PC it is the PC's value, read-only.
            EgCard(Modifier.fillMaxWidth()) {
                Text("Sleep last night", style = EgType.title, color = c.text)
                Spacer(Modifier.height(EgSpace.xs))
                if (onSleepHours != null) {
                    SleepHoursSlider(sleepHours, onSleepHours, height = 48.dp)
                } else {
                    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(hoursText(sleepHours), style = EgType.bodyStrong, color = c.text)
                        Spacer(Modifier.weight(1f))
                        Text("Change on your PC", style = EgType.label, color = c.label)
                    }
                }
            }
        }
        Last7DaysCard(state, Modifier.fillMaxWidth(), chartHeight = 96.dp, compact = true)
        // No API key ever goes on the phone: its coach is the offline templates (live, the PC's coach note is shown).
        SettingsCard(state.settings, settingsActions, coachUnavailable = "no key", hapticsWhat = "Phone", modifier = Modifier.fillMaxWidth())
        EgCard(Modifier.fillMaxWidth()) {
            Text("Privacy", style = EgType.title, color = c.text)
            Spacer(Modifier.height(EgSpace.xs))
            Text(
                "The phone only reads numbers from your PC (score, battery, blink rate…). It never receives images, audio or keystrokes, and it can't change anything on the PC.",
                style = EgType.body, color = c.text,
            )
        }
    }
}

@Composable
private fun linkStatus(link: PhoneLink, error: String?): Pair<String, Color> {
    val c = Eg.colors
    return when {
        error != null -> error to c.exhausted
        link is PhoneLink.Live -> "Live from ${link.pc}" to c.accent
        link is PhoneLink.Connecting -> "Connecting to ${link.pc}…" to c.ice
        link is PhoneLink.Offline -> "Can't reach ${link.pc} · retrying" to c.tiredFill
        else -> "Not connected · showing demo data" to c.muted
    }
}

/** IP, port, code: what the PC dashboard shows ("Phone: 192.168.43.20:8767 · code 123456"). */
@Composable
private fun PairingForm(
    savedHost: String,
    savedPort: Int,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onConnect: (PairingTarget) -> Unit,
) {
    val c = Eg.colors
    var host by remember { mutableStateOf(savedHost) }
    var port by remember { mutableStateOf(savedPort.toString()) }
    var code by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    EgCard(Modifier.fillMaxWidth()) {
        Text("Connect to my PC", style = EgType.heading, color = c.text)
        Spacer(Modifier.height(EgSpace.xs))
        Text(
            "Same Wi-Fi as the PC (e.g. this phone's hotspot). On your PC, turn on phone sharing, then type the address and code from its Profile tab.",
            style = EgType.body, color = c.muted,
        )
        if (invalid) {
            Spacer(Modifier.height(EgSpace.xs))
            Text("Check the IP, the port (1-65535) and the 6-digit code", style = EgType.bodyStrong, color = c.exhausted)
        }
        Spacer(Modifier.height(EgSpace.s))
        OutlinedTextField(
            host, { host = it.take(253); invalid = false }, Modifier.fillMaxWidth().tracksTextFocus(),
            label = { Text("PC address, e.g. 192.168.43.20") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(EgSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(EgSpace.xs)) {
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit).take(5); invalid = false }, Modifier.weight(1f).tracksTextFocus(),
                label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                code, { code = it.filter(Char::isDigit).take(10); invalid = false }, Modifier.weight(1f).tracksTextFocus(),
                label = { Text("Code") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        }
        Spacer(Modifier.height(EgSpace.s))
        EgButton("Connect", { PairingTarget.parse(host, port, code)?.let(onConnect) ?: run { invalid = true } }, Modifier.fillMaxWidth())
        if (canCancel) {
            Spacer(Modifier.height(EgSpace.xs))
            EgButton("Cancel", onCancel, Modifier.fillMaxWidth(), primary = false)
        }
    }
}
