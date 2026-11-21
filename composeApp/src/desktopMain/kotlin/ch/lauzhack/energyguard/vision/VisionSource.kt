package ch.lauzhack.energyguard.vision

import ch.lauzhack.energyguard.demoTiredness
import ch.lauzhack.energyguard.model.VisionFrame
import ch.lauzhack.energyguard.noise
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.random.Random
/** Stream of eye/face metrics (format: docs/API.md). */
interface VisionSource {
    fun frames(): Flow<VisionFrame>
}

/** Fake frames at 10 Hz, getting "tired" over time ([tiredness] in 0..1). Lets the UI work without the Python sidecar. */
class MockVisionSource(private val tiredness: () -> Double = ::demoTiredness) : VisionSource {
    override fun frames(): Flow<VisionFrame> = flow {
        while (true) {
            emit(mockVisionFrame(System.currentTimeMillis(), tiredness()))
            delay(100)
        }
    }
}

/** One fake frame for a given tiredness (0 = fresh, 1 = exhausted). Also used by the demo-mode test. */
internal fun mockVisionFrame(ts: Long, tired: Double, rnd: Random = Random.Default): VisionFrame {
    val ear = 0.30 - 0.10 * tired + noise(0.01, rnd)
    return VisionFrame(
        ts = ts,
        face = true,
        ear = ear,
        eyesClosed = ear < 0.21,
        blinkCount = (14 + 10 * tired + noise(2.0, rnd)).toInt(),
        lastBlinkMs = (160 + 300 * tired + noise(20.0, rnd)).toInt(),
        perclos = 0.05 + 0.30 * tired + noise(0.01, rnd),
        yawn = tired > 0.8 && rnd.nextDouble() < 0.01,
        headPitch = -4.0 - 10 * tired + noise(1.0, rnd),
        headYaw = noise(3.0, rnd),
        source = "mock",
    )
}

/**
 * Real client for the Python sidecar in vision/. Never throws: if the sidecar is down or restarts,
 * it logs and reconnects every [retryMs]; meanwhile the app runs on input only.
 * Frames are re-stamped on arrival with [clock] (one time base for vision + input, see docs/API.md).
 */
class WebSocketVisionSource(
    private val url: String = "ws://localhost:8765",
    private val retryMs: Long = 2_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : VisionSource {
    private val client = HttpClient(CIO) {
        install(WebSockets) { pingIntervalMillis = 2_000 } // detect a hung sidecar, not only a closed one
    }

    override fun frames(): Flow<VisionFrame> = reconnecting(retryMs, ::println) { emit ->
        client.webSocket(url) {
            println("[vision] connected to $url")
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                parseVisionMessage(frame.readText(), clock())?.let { emit(it) }
            }
        }
        println("[vision] $url closed the connection")
    }
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses one sidecar message and stamps it with [arrivalTs] (epoch ms, same clock as input stats).
 * The sidecar's own `ts` is ignored: it comes from another process/clock and is untrusted.
 * Returns null for malformed or non-"vision" messages.
 */
internal fun parseVisionMessage(text: String, arrivalTs: Long): VisionFrame? =
    runCatching { json.decodeFromString<VisionFrame>(text) }
        .onFailure { println("[vision] bad message: ${it.message?.take(120)}") }
        .getOrNull()
        ?.takeIf { it.type == "vision" }
        ?.copy(ts = arrivalTs)

/**
 * Runs [session] forever: when it ends or throws (connection refused, sidecar killed...), logs and retries
 * after [retryMs]. Only cancellation of the collector stops it.
 */
internal fun <T> reconnecting(
    retryMs: Long,
    log: (String) -> Unit,
    session: suspend (emit: suspend (T) -> Unit) -> Unit,
): Flow<T> = channelFlow {
    while (true) {
        try {
            session { send(it) }
        } catch (e: Exception) {
            // A ktor ping timeout can surface as a CancellationException: only stop if WE are cancelled.
            ensureActive()
            log("[vision] unavailable (${e.message ?: e::class.simpleName}), retrying in ${retryMs / 1000.0} s")
        }
        delay(retryMs)
    }
}
