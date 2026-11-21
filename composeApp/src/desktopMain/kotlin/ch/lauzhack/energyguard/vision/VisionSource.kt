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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/** Stream of eye/face metrics (format: docs/API.md). */
interface VisionSource {
    fun frames(): Flow<VisionFrame>
}

/** Fake frames at 10 Hz, getting "tired" over time. Lets the UI work without the Python sidecar. */
class MockVisionSource : VisionSource {
    override fun frames(): Flow<VisionFrame> = flow {
        while (true) {
            val tired = demoTiredness()
            val ear = 0.30 - 0.10 * tired + noise(0.01)
            emit(
                VisionFrame(
                    ts = System.currentTimeMillis(),
                    face = true,
                    ear = ear,
                    eyesClosed = ear < 0.21,
                    blinkCount = (14 + 10 * tired + noise(2.0)).toInt(),
                    lastBlinkMs = (160 + 300 * tired + noise(20.0)).toInt(),
                    perclos = 0.05 + 0.30 * tired + noise(0.01),
                    yawn = tired > 0.8 && Math.random() < 0.01,
                    headPitch = -4.0 - 10 * tired + noise(1.0),
                    headYaw = noise(3.0),
                    source = "mock",
                )
            )
            delay(100)
        }
    }
}

/** Real client for the Python sidecar in vision/. Reconnects every 2 s if the sidecar is down. */
class WebSocketVisionSource(private val url: String = "ws://localhost:8765") : VisionSource {
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val json = Json { ignoreUnknownKeys = true }

    override fun frames(): Flow<VisionFrame> = channelFlow {
        while (true) {
            try {
                client.webSocket(url) {
                    println("[vision] connected to $url")
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val parsed = runCatching { json.decodeFromString<VisionFrame>(frame.readText()) }
                        parsed.onSuccess { if (it.type == "vision") send(it) }
                            .onFailure { println("[vision] bad message: ${it.message}") }
                    }
                }
            } catch (e: Exception) {
                println("[vision] $url unavailable (${e.message}), retrying in 2 s")
            }
            delay(2000)
        }
    }
}
