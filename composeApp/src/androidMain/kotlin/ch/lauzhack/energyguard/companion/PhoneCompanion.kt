package ch.lauzhack.energyguard.companion

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import ch.lauzhack.energyguard.ui.phone.PhoneLink
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

/** Why the live view stopped (shown on the Profile tab). */
enum class StopReason(val message: String) {
    WRONG_CODE("Wrong code: type the one your PC shows now"),
    TOO_MANY("Too many wrong codes: wait 1 minute, then retry"),
}

/** The poller's view: [state] = the PC's last snapshot while [link] is Live, else null. */
data class CompanionLink(val state: CompanionState?, val link: PhoneLink)

private const val POLL_MS = 1_000L
private const val FAILURES_BEFORE_OFFLINE = 3

/**
 * Live companion (#48): polls the PC's read-only `GET /state` every ~1 s (contract: docs/API.md "Phone companion").
 * 3 failures in a row -> Offline (the shell shows its demo replay meanwhile), keeps retrying.
 * Wrong code / too many tries -> [onStop] and polling stops. No [target] -> NotPaired, nothing is sent.
 */
@Composable
fun rememberCompanionLink(target: PairingTarget?, onStop: (StopReason) -> Unit): CompanionLink {
    val client = remember { CompanionClient(CIO) }
    DisposableEffect(client) { onDispose { client.close() } }
    val stop by rememberUpdatedState(onStop)
    var last by remember(target) { mutableStateOf<CompanionState?>(null) }
    var failures by remember(target) { mutableIntStateOf(0) }

    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        while (true) {
            when (val r = client.fetch(target)) {
                is CompanionResult.Ok -> { last = r.state; failures = 0 }
                CompanionResult.WrongCode -> { stop(StopReason.WRONG_CODE); return@LaunchedEffect }
                CompanionResult.TooManyTries -> { stop(StopReason.TOO_MANY); return@LaunchedEffect }
                is CompanionResult.Unreachable -> failures++
            }
            delay(POLL_MS)
        }
    }

    if (target == null) return CompanionLink(null, PhoneLink.NotPaired)
    val pc = "${target.host}:${target.port}"
    val state = last
    return when {
        state != null && failures < FAILURES_BEFORE_OFFLINE -> CompanionLink(state, PhoneLink.Live(pc))
        state == null && failures < FAILURES_BEFORE_OFFLINE -> CompanionLink(null, PhoneLink.Connecting(pc))
        else -> CompanionLink(null, PhoneLink.Offline(pc))
    }
}
