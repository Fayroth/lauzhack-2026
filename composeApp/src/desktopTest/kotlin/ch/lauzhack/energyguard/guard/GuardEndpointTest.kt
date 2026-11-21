package ch.lauzhack.energyguard.guard

import ch.lauzhack.energyguard.guardrail.GuardedAction
import ch.lauzhack.energyguard.guardrail.Guardrail
import ch.lauzhack.energyguard.guardrail.Verdict
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardEndpointTest {
    private val origin = "chrome-extension://$DEFAULT_EXTENSION_ID"
    private val host = "127.0.0.1:$DEFAULT_GUARD_PORT"
    private val phishing = """{"displayText":"https://www.paypal.com/signin","href":"https://paypa1-secure.com/signin","context":""}"""
    private val legit = """{"displayText":"EPFL news","href":"https://actu.epfl.ch/news/"}"""

    private var fatigue = FatigueState(10, Level.FRESH, emptyList(), calibrated = true)
    private var hapticPulses = 0
    private val guardrail = Guardrail()
    /** Mirrors main.kt's `guard`: Guardrail + latest fatigue + haptic on Block. */
    private val guard: (GuardedAction) -> Verdict = { a -> guardrail.check(a, fatigue).also { if (it is Verdict.Block) hapticPulses++ } }
    private val logs = mutableListOf<String>()
    private val endpoint = GuardEndpoint(guard, { fatigue.level }, log = { logs += it })

    private fun call(
        body: String,
        origin: String? = this.origin,
        host: String? = this.host,
        method: String = "POST",
        path: String = "/guard",
        length: Long? = body.length.toLong(),
    ): Reply = runBlocking { endpoint.handle(method, path, host, origin, length) { body } }

    private fun verdictOf(r: Reply) = Json.decodeFromString<GuardResponse>(r.body)

    @Test fun rejectsAnyOriginButOurExtension() {
        for (bad in listOf(null, "https://evil.com", "http://127.0.0.1:8766", "chrome-extension://abcdefghijklmnopabcdefghijklmnop", "null")) {
            assertEquals(403, call(phishing, origin = bad).status, "origin $bad must be rejected")
        }
        var bodyRead = false
        runBlocking { endpoint.handle("POST", "/guard", host, "https://evil.com", 10) { bodyRead = true; phishing } }
        assertFalse(bodyRead, "body must not even be read for a foreign origin")
        assertEquals(0, endpoint.requestCount.get())
    }

    @Test fun rejectsForeignHostHeader() { // DNS rebinding: evil.com resolving to 127.0.0.1
        assertEquals(403, call(phishing, host = "evil.com:8766").status)
        assertEquals(403, call(phishing, host = null).status)
        assertEquals(200, call(legit, host = "localhost:$DEFAULT_GUARD_PORT").status)
    }

    @Test fun validatesInput() {
        assertEquals(404, call(phishing, path = "/").status)
        assertEquals(405, call(phishing, method = "GET").status)
        assertEquals(411, call(phishing, length = null).status)
        assertEquals(413, call(phishing, length = 1_000_000).status)
        assertEquals(400, call("not json").status)
        assertEquals(400, call("""{"displayText":"x"}""").status) // no href
        assertEquals(400, call("""{"displayText":"x","href":"javascript:alert(1)"}""").status)
        assertEquals(400, call("""{"displayText":"x","href":"file:///etc/passwd"}""").status)
        assertEquals(400, call("""{"displayText":"x","href":"https://a.com/\u0000b"}""").status)
        assertEquals(400, call("""{"displayText":"x","href":"https://a.com/${"a".repeat(3000)}"}""").status)
        assertEquals(400, call("""{"displayText":"${"a".repeat(2000)}","href":"https://a.com/"}""").status)
        assertEquals(0, hapticPulses)
        assertEquals(0, endpoint.requestCount.get())
    }

    @Test fun mapsVerdictsLikeTheInbox() {
        verdictOf(call(phishing)).let { assertEquals("warn", it.verdict); assertEquals("FRESH", it.level); assertTrue(it.signals.isNotEmpty()) }
        verdictOf(call(legit)).let { assertEquals("allow", it.verdict); assertEquals("", it.reason); assertEquals(emptyList(), it.signals) }
        assertEquals(0, hapticPulses)

        fatigue = FatigueState(65, Level.TIRED, emptyList(), calibrated = true)
        val blocked = verdictOf(call(phishing))
        assertEquals("block", blocked.verdict)
        assertEquals("TIRED", blocked.level)
        assertTrue(blocked.signals.any { "paypa1-secure.com" in it }, blocked.signals.toString())
        assertEquals(1, hapticPulses, "Block must vibrate the mouse once, same path as LocalGuard")
        assertEquals("allow", verdictOf(call(legit)).verdict)
        assertEquals(4, endpoint.requestCount.get())
    }

    @Test fun logsNeverContainTheLink() {
        call(phishing); call(legit)
        assertEquals(2, logs.size)
        assertTrue(logs.none { "paypa1" in it || "epfl" in it || "http" in it }, logs.toString())
    }

    @Test fun guardCrashIsA500SoTheExtensionFailsOpen() {
        val broken = GuardEndpoint({ error("boom") }, { Level.FRESH }, log = {})
        assertEquals(500, runBlocking { broken.handle("POST", "/guard", host, origin, legit.length.toLong()) { legit } }.status)
    }

    @Test fun realServerOnLoopbackChecksOrigin() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        fatigue = FatigueState(65, Level.TIRED, emptyList(), calibrated = true)
        val server = GuardServer(GuardEndpoint(guard, { fatigue.level }, port = port, log = {}), port)
        assertTrue(server.start())
        val client = HttpClient(CIO)
        try {
            val ok = client.post("http://127.0.0.1:$port/guard") {
                header("Origin", origin); contentType(ContentType.Application.Json); setBody(phishing)
            }
            assertEquals(200, ok.status.value)
            assertEquals("block", Json.decodeFromString<GuardResponse>(ok.bodyAsText()).verdict)
            val evil = client.post("http://127.0.0.1:$port/guard") {
                header("Origin", "https://evil.com"); contentType(ContentType.Application.Json); setBody(phishing)
            }
            assertEquals(403, evil.status.value)
            assertTrue(evil.headers["Access-Control-Allow-Origin"] == null, "no CORS for web pages")
        } finally {
            client.close(); server.stop()
        }
    }
}
