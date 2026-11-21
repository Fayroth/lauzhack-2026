package ch.lauzhack.energyguard.guard

import ch.lauzhack.energyguard.guardrail.GuardedAction
import ch.lauzhack.energyguard.guardrail.Verdict
import ch.lauzhack.energyguard.model.Level
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/** Extension id derived from the public `key` pinned in extension/manifest.json. */
const val DEFAULT_EXTENSION_ID = "mchiibilichfmibgecnoaheikiajhclo"
const val DEFAULT_GUARD_PORT = 8766

@Serializable
data class GuardRequest(val displayText: String, val href: String, val context: String = "")

@Serializable
data class GuardResponse(val verdict: String, val reason: String, val signals: List<String>, val level: String)

data class Reply(val status: Int, val body: String)

/**
 * Pure request handling for `POST /guard` (contract + security rules in docs/API.md, #43).
 * [guard] is the SAME function main.kt gives to LocalGuard (Guardrail + latest fatigue + haptic on Block): no second copy of the rules.
 * Logs nothing but a counter and the verdict; never the URL or the text.
 */
class GuardEndpoint(
    private val guard: (GuardedAction) -> Verdict,
    private val level: () -> Level,
    extensionId: String = DEFAULT_EXTENSION_ID,
    port: Int = DEFAULT_GUARD_PORT,
    private val log: (String) -> Unit = ::println,
) {
    private val allowedOrigin = "chrome-extension://$extensionId"
    private val allowedHosts = setOf("127.0.0.1:$port", "localhost:$port")
    val requestCount = AtomicInteger()

    /** [body] is read only once the request passed the method/path/host/origin/size checks. */
    suspend fun handle(method: String, path: String, host: String?, origin: String?, contentLength: Long?, body: suspend () -> String): Reply {
        if (path != "/guard") return error(404, "not found")
        if (method != "POST") return error(405, "method not allowed")
        if (host == null || host.lowercase() !in allowedHosts) return error(403, "forbidden host")
        if (origin != allowedOrigin) return error(403, "forbidden origin")
        if (contentLength == null) return error(411, "content-length required")
        if (contentLength > MAX_BODY_BYTES) return error(413, "body too large")

        val text = runCatching { body() }.getOrElse { return error(400, "unreadable body") }
        if (text.length > MAX_BODY_BYTES) return error(413, "body too large")
        val req = try {
            JSON.decodeFromString<GuardRequest>(text)
        } catch (_: SerializationException) {
            return error(400, "invalid json")
        } catch (_: IllegalArgumentException) {
            return error(400, "invalid json")
        }
        validate(req)?.let { return error(400, it) }

        val verdict = try {
            guard(GuardedAction.OpenLink(req.displayText, req.href.trim(), req.context))
        } catch (e: Exception) {
            return error(500, "guard failed") // extension fails open
        }
        val n = requestCount.incrementAndGet()
        val response = when (verdict) {
            Verdict.Allow -> GuardResponse("allow", "", emptyList(), level().name)
            is Verdict.Warn -> GuardResponse("warn", verdict.reason, verdict.signals, level().name)
            is Verdict.Block -> GuardResponse("block", verdict.reason, verdict.signals, level().name)
        }
        log("[guard] check #$n -> ${response.verdict}")
        return Reply(200, JSON.encodeToString(GuardResponse.serializer(), response))
    }

    private fun validate(r: GuardRequest): String? {
        val href = r.href.trim()
        return when {
            r.displayText.length > MAX_TEXT -> "displayText too long"
            r.context.length > MAX_CONTEXT -> "context too long"
            href.isEmpty() || href.length > MAX_HREF -> "bad href length"
            !(href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) -> "only http/https links"
            href.any { it.isWhitespace() || it.isISOControl() } -> "bad characters in href"
            else -> null
        }
    }

    private fun error(status: Int, message: String) = Reply(status, """{"error":"$message"}""")

    companion object {
        const val MAX_BODY_BYTES = 16 * 1024L
        const val MAX_TEXT = 1000
        const val MAX_HREF = 2048
        const val MAX_CONTEXT = 2000
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** Thin Ktor CIO wrapper around [GuardEndpoint], bound to 127.0.0.1 ONLY. */
class GuardServer(private val endpoint: GuardEndpoint, private val port: Int) {
    private var server: EmbeddedServer<*, *>? = null

    /** Never throws: if the port is busy the app keeps running and Chrome links just open unguarded (fail open). */
    fun start(): Boolean = try {
        server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            intercept(ApplicationCallPipeline.Call) {
                val req = call.request
                val reply = endpoint.handle(
                    method = req.httpMethod.value,
                    path = req.path(),
                    host = req.headers[HttpHeaders.Host],
                    origin = req.headers[HttpHeaders.Origin],
                    contentLength = req.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                ) { call.receiveText() }
                call.respondText(reply.body, ContentType.Application.Json, HttpStatusCode.fromValue(reply.status))
                finish()
            }
        }.start(wait = false)
        println("[guard] Chrome extension endpoint on http://127.0.0.1:$port/guard")
        true
    } catch (e: Exception) {
        System.err.println("[guard] could not listen on 127.0.0.1:$port (${e.javaClass.simpleName}); Chrome links will open unguarded")
        server = null
        false
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null
    }

    companion object {
        /** Port from EG_GUARD_PORT (default 8766), extension id from EG_EXTENSION_ID (default: the pinned one). */
        fun fromEnv(guard: (GuardedAction) -> Verdict, level: () -> Level): GuardServer {
            val port = System.getenv("EG_GUARD_PORT")?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: DEFAULT_GUARD_PORT
            val id = System.getenv("EG_EXTENSION_ID")?.trim()?.takeIf { Regex("^[a-p]{32}$").matches(it) } ?: DEFAULT_EXTENSION_ID
            return GuardServer(GuardEndpoint(guard, level, id, port), port)
        }
    }
}
