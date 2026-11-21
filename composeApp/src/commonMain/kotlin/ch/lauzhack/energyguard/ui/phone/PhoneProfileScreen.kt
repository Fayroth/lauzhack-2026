package ch.lauzhack.energyguard.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import ch.lauzhack.energyguard.app.ThemeMode
import ch.lauzhack.energyguard.companion.PairingTarget
import ch.lauzhack.energyguard.ui.components.Eg
import ch.lauzhack.energyguard.ui.components.EgButton
import ch.lauzhack.energyguard.ui.components.EgCard
import ch.lauzhack.energyguard.ui.components.EgSpace
import ch.lauzhack.energyguard.ui.components.EgType
import ch.lauzhack.energyguard.ui.components.tracksTextFocus

/**
 * Profile tab on the phone (M3): the PC pairing (status, "Change PC", the IP / port / code form) and the theme.
 * The pairing itself is saved by the Android shell (private prefs); this screen only validates through
 * [PairingTarget.parse], so the URL is never built from raw text.
 */
@Composable
fun PhoneProfileScreen(
    link: PhoneLink,
    savedHost: String,
    savedPort: Int,
    error: String?,
    theme: ThemeMode,
    onConnect: (PairingTarget) -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Eg.colors
    var editing by remember { mutableStateOf(false) }
    val showForm = editing || link == PhoneLink.NotPaired || error != null
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EgSpace.s, vertical = EgSpace.xs),
        verticalArrangement = Arrangement.spacedBy(EgSpace.s),
    ) {
        EgCard(Modifier.fillMaxWidth()) {
            Text("Your PC", style = EgType.title, color = c.muted)
            Spacer(Modifier.height(EgSpace.xs))
            val (status, color) = linkStatus(link, error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
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
        EgCard(Modifier.fillMaxWidth()) {
            Text("Privacy", style = EgType.title, color = c.muted)
            Spacer(Modifier.height(EgSpace.xs))
            Text(
                "The phone only reads numbers from your PC (score, battery, blink rate…). It never receives images, audio or keystrokes, and it can't change anything on the PC.",
                style = EgType.body, color = c.text,
            )
        }
        EgCard(Modifier.fillMaxWidth()) {
            Text("Appearance", style = EgType.title, color = c.muted)
            Spacer(Modifier.height(EgSpace.xs))
            EgButton(if (theme == ThemeMode.LIGHT) "Switch to dark mode" else "Switch to light mode", onToggleTheme, Modifier.fillMaxWidth(), primary = false)
        }
    }
}

@Composable
private fun linkStatus(link: PhoneLink, error: String?): Pair<String, Color> {
    val c = Eg.colors
    return when {
        error != null -> error to c.exhausted
        link is PhoneLink.Live -> "Live from ${link.pc}" to c.fresh
        link is PhoneLink.Connecting -> "Connecting to ${link.pc}…" to c.accent
        link is PhoneLink.Offline -> "Can't reach ${link.pc} · retrying" to c.tired
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
        Text("Connect to my PC", style = EgType.title, color = c.text)
        Spacer(Modifier.height(EgSpace.xs))
        Text(
            "Same Wi-Fi as the PC (e.g. this phone's hotspot). On your PC, turn on phone sharing and type the address and code shown at the bottom.",
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
