package ch.lauzhack.energyguard.companion

import ch.lauzhack.energyguard.app.AppState
import ch.lauzhack.energyguard.app.BreakSource
import ch.lauzhack.energyguard.app.CoachNote
import ch.lauzhack.energyguard.app.Concentration
import ch.lauzhack.energyguard.app.DayStats
import ch.lauzhack.energyguard.app.EnergyHistory
import ch.lauzhack.energyguard.app.EnergyState
import ch.lauzhack.energyguard.app.Settings
import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.engine.BaselineSummary
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.DashboardState
import ch.lauzhack.energyguard.ui.today.SignalsSnapshot
import ch.lauzhack.energyguard.ui.today.liveSignals
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phone companion (#48): what `GET /state` returns. Contract in docs/API.md ("Phone companion").
 * Numbers and the engine's short reason / coach strings only: never images, audio, keystrokes, URLs or guardrail data.
 */
@Serializable
data class CompanionState(
    val score: Int,
    val level: Level,
    val reasons: List<String>,
    val calibrated: Boolean,
    val coach: CompanionCoach? = null,
    val ts: Long,
    val llmCalls: Int = 0,
    val calibrationMs: Long = 60_000,
    val windowMs: Long = 60_000,
    val runId: Int = 0,
    val vision: VisionFrame? = null,
    val input: InputStats? = null,
    // --- Optional, app model (M1): absent from older PCs, ignored by older phones (ignoreUnknownKeys) ---
    val energy: CompanionEnergy? = null,
    val recovery: Int? = null,
    val stats: CompanionStats? = null,
    // --- Optional, M3: the Live signals card as numbers (the phone has no personal baseline to compute "× usual") ---
    val signals: SignalsSnapshot? = null,
    // --- Optional, M4.5: today's battery curve for the Energy chart (EnergyHistory, one value per 5 min) ---
    val energyHistory: List<Int>? = null,
)

@Serializable
data class CompanionCoach(val text: String, val kind: Intervention, val fromLlm: Boolean)

/**
 * EnergyBattery for the phone. [breakSource] = "USER" | "AUTO" | null.
 * M3 (optional, null from older PCs): [startFromSleep] = battery start this morning, [sleepHours] = the PC's slider.
 */
@Serializable
data class CompanionEnergy(
    val battery: Int,
    val onBreak: Boolean = false,
    val breakSource: BreakSource? = null,
    val startFromSleep: Int? = null,
    val sleepHours: Double? = null,
)

/** Today's DayStats, numbers only. Never journal entries or notes. */
@Serializable
data class CompanionStats(
    val focusedMs: Long = 0,
    val breaksTaken: Int = 0,
    val breaksNeeded: Int = 0,
    val longestFocusMs: Long = 0,
    val phishingBlocked: Int = 0,
    val llmCalls: Int = 0,
    val framesProcessed: Long = 0,
    val imagesSent: Int = 0,
    val audioSent: Int = 0,
    val keysSent: Int = 0,
)

/** PC side: the app-model fields (battery, recovery, today's stats) on top of the dashboard snapshot. */
fun CompanionState.withApp(app: AppState) = copy(
    energy = CompanionEnergy(
        app.energy.battery, app.energy.onBreak, app.energy.breakSource, app.energy.startFromSleep, app.settings.sleepHours,
    ),
    signals = app.liveSignals(),
    energyHistory = app.energyHistory.values,
    recovery = app.recovery,
    stats = with(app.stats) {
        CompanionStats(focusedMs, breaksTaken, breaksNeeded, longestFocusMs, phishingBlocked, llmCalls, framesProcessed,
            imagesSent, audioSent, keysSent)
    },
)

/** PC side: full snapshot from the app store. */
fun AppState.toCompanionState(ts: Long) = dashboard.toCompanionState(ts).withApp(this)

/** Phone side: clamp the optional app-model fields (untrusted network), keep them null when the PC is older. */
fun CompanionState.clampAppFields() = copy(
    energy = energy?.let {
        it.copy(
            battery = it.battery.coerceIn(0, 100),
            startFromSleep = it.startFromSleep?.coerceIn(0, 100),
            sleepHours = it.sleepHours?.takeIf { h -> h.isFinite() }?.coerceIn(Settings.MIN_SLEEP_HOURS, Settings.MAX_SLEEP_HOURS),
        )
    },
    signals = signals?.clamped(),
    energyHistory = energyHistory?.takeLast(EnergyHistory.MAX_SAMPLES)?.map { it.coerceIn(0, 100) },
    recovery = recovery?.coerceIn(0, 100),
    stats = stats?.let {
        it.copy(
            focusedMs = it.focusedMs.coerceAtLeast(0), breaksTaken = it.breaksTaken.coerceAtLeast(0),
            breaksNeeded = it.breaksNeeded.coerceAtLeast(0), longestFocusMs = it.longestFocusMs.coerceAtLeast(0),
            phishingBlocked = it.phishingBlocked.coerceAtLeast(0), llmCalls = it.llmCalls.coerceAtLeast(0),
            framesProcessed = it.framesProcessed.coerceAtLeast(0), imagesSent = it.imagesSent.coerceAtLeast(0),
            audioSent = it.audioSent.coerceAtLeast(0), keysSent = it.keysSent.coerceAtLeast(0),
        )
    },
)

/**
 * Phone side (M3): the PC's state as an [AppState] for the shared Today screen. Call on a [clampAppFields] result.
 * [local] gives what never comes from the PC (theme, haptics switch, the phone's own journal). Fields an older PC
 * doesn't send keep neutral defaults; the battery then mirrors the concentration's scale, not a made-up number.
 */
fun CompanionState.toAppState(local: AppState, day: String): AppState {
    val dashboard = toDashboardState()
    val e = energy
    return local.copy(
        dashboard = dashboard,
        concentration = Concentration.from(dashboard.fatigue),
        energy = if (e == null) EnergyState() else EnergyState(e.battery, e.onBreak, e.breakSource, e.startFromSleep ?: e.battery),
        recovery = recovery ?: 0,
        coach = dashboard.coach?.let { CoachNote(it.text, it.kind, it.fromLlm) },
        stats = stats?.let {
            DayStats(day, it.focusedMs, it.breaksTaken, it.breaksNeeded, it.longestFocusMs, 0, it.phishingBlocked, it.llmCalls,
                it.framesProcessed, it.imagesSent, it.audioSent, it.keysSent)
        } ?: DayStats(day, llmCalls = dashboard.llmCallCount),
        settings = e?.sleepHours?.let { local.settings.copy(sleepHours = it) } ?: local.settings,
        demo = false,
        energyHistory = EnergyHistory(values = energyHistory ?: e?.let { listOf(it.battery) } ?: emptyList()),
    )
}

/** Phone side: the PC's Live signals rows; an older PC without `signals` -> computed from raw vision/input (no baseline). */
fun CompanionState.phoneSignals(): SignalsSnapshot =
    signals ?: SignalsSnapshot.of(vision, input, BaselineSummary.Empty, demo = false)

/** PC side: snapshot of what the dashboard shows right now. */
fun DashboardState.toCompanionState(ts: Long) = CompanionState(
    score = fatigue.score,
    level = fatigue.level,
    reasons = fatigue.reasons,
    calibrated = fatigue.calibrated,
    coach = coach?.let { CompanionCoach(it.text, it.kind, it.fromLlm) },
    ts = ts,
    llmCalls = llmCallCount,
    calibrationMs = calibrationMs,
    windowMs = windowMs,
    runId = runId,
    vision = vision,
    input = input,
)

/** Phone side: the network is untrusted, so everything is clamped before it reaches the UI. */
fun CompanionState.toDashboardState() = DashboardState(
    fatigue = FatigueState(
        score = score.coerceIn(0, 100),
        level = level,
        reasons = reasons.take(MAX_REASONS).map { it.take(MAX_REASON_CHARS) },
        calibrated = calibrated,
    ),
    vision = vision,
    input = input,
    coach = coach?.let { CoachMessage(it.text.take(MAX_COACH_CHARS), it.fromLlm, it.kind) },
    llmCallCount = llmCalls.coerceAtLeast(0),
    calibrationMs = calibrationMs.coerceIn(1_000, 600_000),
    windowMs = windowMs.coerceIn(1_000, 600_000),
    runId = runId,
)

private const val MAX_REASONS = 5
private const val MAX_REASON_CHARS = 60
private const val MAX_COACH_CHARS = 200

val CompanionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

/** Where the phone looks for the PC. Built only through [parse], so the URL can never be injected. */
data class PairingTarget(val host: String, val port: Int, val code: String) {
    companion object {
        const val DEFAULT_PORT = 8767
        private val HOST = Regex("^[A-Za-z0-9.-]{1,253}$")
        private val CODE = Regex("^[0-9]{6,10}$")

        fun parse(host: String, port: String, code: String): PairingTarget? {
            val h = host.trim()
            val p = port.trim().ifEmpty { DEFAULT_PORT.toString() }.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val c = code.trim()
            if (!HOST.matches(h) || !CODE.matches(c)) return null
            return PairingTarget(h, p, c)
        }
    }
}

sealed interface CompanionResult {
    data class Ok(val state: CompanionState) : CompanionResult
    data object WrongCode : CompanionResult
    data object TooManyTries : CompanionResult
    data class Unreachable(val why: String) : CompanionResult
}

/** Read-only client for the PC's `GET /state`. [engine] is per platform (CIO on Android and desktop). [fetch] never throws. */
class CompanionClient(engine: HttpClientEngineFactory<*>, private val timeoutMs: Long = 1_500) : AutoCloseable {
    private val http = HttpClient(engine) {
        install(HttpTimeout)
        expectSuccess = false
        followRedirects = false
    }

    override fun close() = http.close()

    suspend fun fetch(target: PairingTarget): CompanionResult = try {
        val resp = http.get {
            url {
                protocol = URLProtocol.HTTP
                host = target.host
                port = target.port
                path("state")
            }
            header(HttpHeaders.Authorization, "Bearer ${target.code}")
            timeout { requestTimeoutMillis = timeoutMs; connectTimeoutMillis = timeoutMs; socketTimeoutMillis = timeoutMs }
        }
        when (resp.status.value) {
            200 -> {
                val text = resp.bodyAsText()
                if (text.length > MAX_BODY_CHARS) CompanionResult.Unreachable("response too large")
                else CompanionResult.Ok(CompanionJson.decodeFromString(CompanionState.serializer(), text).clampAppFields())
            }
            401 -> CompanionResult.WrongCode
            429 -> CompanionResult.TooManyTries
            else -> CompanionResult.Unreachable("HTTP ${resp.status.value}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CompanionResult.Unreachable(e::class.simpleName ?: "error")
    }

    private companion object {
        const val MAX_BODY_CHARS = 64 * 1024
    }
}
