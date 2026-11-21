package ch.lauzhack.energyguard.companion

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.lauzhack.energyguard.HapticTrigger
import ch.lauzhack.energyguard.haptics.HapticOutput
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.DashboardState
import ch.lauzhack.energyguard.ui.Warm
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.delay

/** Saved pairing (IP, port, code) in private app prefs (allowBackup=false in the manifest). */
class PairingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    fun load(): PairingTarget? = PairingTarget.parse(
        prefs.getString("host", "") ?: "", prefs.getInt("port", PairingTarget.DEFAULT_PORT).toString(), prefs.getString("code", "") ?: "",
    )

    fun lastHost(): String = prefs.getString("host", "") ?: ""
    fun lastPort(): Int = prefs.getInt("port", PairingTarget.DEFAULT_PORT)

    fun save(t: PairingTarget) = prefs.edit().putString("host", t.host).putInt("port", t.port).putString("code", t.code).apply()
    fun forgetCode() = prefs.edit().remove("code").apply()
}

/** One-time screen: type what the PC dashboard shows ("Phone: 192.168.43.20:8767 · code 123456"). */
@Composable
fun PairingScreen(lastHost: String, lastPort: Int, error: String?, onConnect: (PairingTarget) -> Unit, onDemo: () -> Unit) {
    var host by remember { mutableStateOf(lastHost) }
    var port by remember { mutableStateOf(lastPort.toString()) }
    var code by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Warm.Night).safeDrawingPadding()) {
        Column(
            Modifier.align(Alignment.Center).width(560.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to your PC", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Warm.Text)
            Text("Same Wi-Fi as the PC (e.g. this phone's hotspot). Type what the PC dashboard shows bottom-left.", fontSize = 15.sp, color = Warm.Muted)
            (error ?: if (invalid) "Check the IP, port (1-65535) and the 6-digit code" else null)?.let {
                Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF8A80))
            }
            OutlinedTextField(host, { host = it; invalid = false }, Modifier.fillMaxWidth(), label = { Text("PC address, e.g. 192.168.43.20") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5); invalid = false }, Modifier.weight(1f), label = { Text("Port") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(10); invalid = false }, Modifier.weight(1f), label = { Text("Code") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { PairingTarget.parse(host, port, code)?.let(onConnect) ?: run { invalid = true } }) { Text("Connect", fontSize = 18.sp) }
                OutlinedButton(onClick = onDemo) { Text("Demo without PC", fontSize = 16.sp, color = Warm.Muted) }
            }
        }
    }
}

/** Why the live view stopped (shown on the pairing screen). */
enum class StopReason(val message: String) {
    WRONG_CODE("Wrong code"),
    TOO_MANY("Too many wrong codes: wait 1 minute, then retry"),
}

private const val POLL_MS = 1_000L
private const val FAILURES_BEFORE_OFFLINE = 3
private const val RECOVERED_BANNER_MS = 30_000L

/**
 * Live companion (#48): polls the PC's read-only `GET /state` every ~1 s and renders the shared dashboard ([dashboard]).
 * Vibrates via the shared [HapticTrigger] on the PC's level. Connection lost → [offline] (demo replay + banner), keeps retrying.
 */
@Composable
fun LiveCompanion(
    target: PairingTarget,
    haptics: HapticOutput,
    onStop: (StopReason) -> Unit,
    dashboard: @Composable (DashboardState, banner: String?) -> Unit,
    offline: @Composable (banner: String) -> Unit,
) {
    val client = remember { CompanionClient(CIO) }
    DisposableEffect(client) { onDispose { client.close() } }
    var live by remember(target) { mutableStateOf<DashboardState?>(null) }
    var failures by remember(target) { mutableStateOf(0) }

    LaunchedEffect(target) {
        while (true) {
            when (val r = client.fetch(target)) {
                is CompanionResult.Ok -> { live = r.state.toDashboardState(); failures = 0 }
                CompanionResult.WrongCode -> { onStop(StopReason.WRONG_CODE); return@LaunchedEffect }
                CompanionResult.TooManyTries -> { onStop(StopReason.TOO_MANY); return@LaunchedEffect }
                is CompanionResult.Unreachable -> failures++
            }
            delay(POLL_MS)
        }
    }

    val state = live
    val connected = state != null && failures < FAILURES_BEFORE_OFFLINE
    val level = if (connected) state!!.fatigue.level else null

    // Haptics follow the PC: 1 buzz on TIRED, 3 on EXHAUSTED (same rules as the MX Master 4).
    val trigger = remember(target) { HapticTrigger() }
    // "You can go back": the PC went from TIRED/EXHAUSTED back to FRESH/OK.
    var wasLow by remember(target) { mutableStateOf(false) }
    var recoveredAt by remember(target) { mutableStateOf<Long?>(null) }
    LaunchedEffect(level) {
        if (level == null) return@LaunchedEffect
        val now = System.currentTimeMillis()
        trigger.onLevel(level, now)?.let { haptics.pulse(it) }
        if (level >= Level.TIRED) { wasLow = true; recoveredAt = null }
        else if (wasLow) { wasLow = false; recoveredAt = now }
    }
    LaunchedEffect(recoveredAt) {
        if (recoveredAt != null) { delay(RECOVERED_BANNER_MS); recoveredAt = null }
    }

    if (connected) {
        dashboard(state!!, if (recoveredAt != null) "Your PC says you're recharged: you can go back" else null)
    } else {
        offline(if (state == null && failures < FAILURES_BEFORE_OFFLINE) "Connecting to ${target.host}:${target.port}…"
                else "Not connected to your PC (${target.host}:${target.port}) · retrying · demo replay meanwhile")
    }
}

/** Big banner over the dashboard, readable from a table. */
@Composable
fun CompanionBanner(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.background(color.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
    )
}
