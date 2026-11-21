package ch.lauzhack.energyguard.companion

import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.InputStats
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.ui.DashboardState
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
)

@Serializable
data class CompanionCoach(val text: String, val kind: Intervention, val fromLlm: Boolean)

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
                else CompanionResult.Ok(CompanionJson.decodeFromString(CompanionState.serializer(), text))
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
