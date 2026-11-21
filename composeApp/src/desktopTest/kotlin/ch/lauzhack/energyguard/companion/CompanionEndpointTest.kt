package ch.lauzhack.energyguard.companion

import ch.lauzhack.energyguard.coach.CoachMessage
import ch.lauzhack.energyguard.coach.Intervention
import ch.lauzhack.energyguard.model.FatigueState
import ch.lauzhack.energyguard.model.Level
import ch.lauzhack.energyguard.ui.DashboardState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionEndpointTest {
    private val code = "482915"
    private val auth = "Bearer $code"
    private var now = 1_000_000L
    private val logs = mutableListOf<String>()
    private val dashboard = DashboardState(
        fatigue = FatigueState(62, Level.TIRED, listOf("long blinks +80%", "mouse jitter +40%"), calibrated = true),
        coach = CoachMessage("Eyes closing +80% at 16:20: 5-min walk.", fromLlm = true, kind = Intervention.BREAK),
        llmCallCount = 1, calibrationMs = 15_000, windowMs = 10_000, runId = 2,
    )
    private var snapshotReads = 0
    private val endpoint = CompanionEndpoint(code, { snapshotReads++; dashboard.toCompanionState(now) }, { now }, { logs += it })

    private fun get(authorization: String? = auth, remote: String = "192.168.43.5", method: String = "GET", path: String = "/state") =
        endpoint.handle(method, path, authorization, remote)

    @Test fun codeRequiredOnEveryRequest() {
        assertEquals(200, get().status)
        for (bad in listOf(null, "", code, "Bearer ", "Bearer 000000", "Bearer ${code}0", "bearer $code", "Basic $code")) {
            assertEquals(401, get(bad, remote = "10.0.0.${bad?.length ?: 99}").status, "authorization '$bad' must be rejected")
        }
        assertEquals(1, endpoint.requestCount.get())
        assertEquals(1, snapshotReads, "state must not even be read without the right code")
    }

    @Test fun wrongCodesAreRateLimitedPerIp() {
        repeat(CompanionEndpoint.MAX_FAILURES) { assertEquals(401, get("Bearer 11111$it").status) }
        assertEquals(429, get().status, "locked out, even with the right code")
        assertEquals(200, get(remote = "192.168.43.9").status, "another device is not locked")
        now += CompanionEndpoint.LOCK_MS + 1
        assertEquals(200, get().status, "lock expires")
    }

    @Test fun oldFailuresDoNotAddUp() {
        repeat(CompanionEndpoint.MAX_FAILURES - 1) { get("Bearer 999999") }
        now += CompanionEndpoint.WINDOW_MS + 1
        assertEquals(401, get("Bearer 999999").status)
        assertEquals(200, get().status)
    }

    @Test fun readOnly() {
        for (m in listOf("POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")) assertEquals(405, get(method = m).status, m)
        for (p in listOf("/", "/guard", "/state/", "/state/../guard", "/admin")) assertEquals(404, get(path = p).status, p)
        assertEquals(0, snapshotReads)
    }

    @Test fun jsonShapeIsTheContract() {
        val body = Json.parseToJsonElement(get().body).jsonObject
        assertEquals(
            setOf("score", "level", "reasons", "calibrated", "coach", "ts", "llmCalls", "calibrationMs", "windowMs", "runId", "vision", "input"),
            body.keys,
        )
        val s = CompanionJson.decodeFromString(CompanionState.serializer(), get().body)
        assertEquals(62, s.score); assertEquals(Level.TIRED, s.level); assertEquals(now, s.ts)
        assertEquals(CompanionCoach("Eyes closing +80% at 16:20: 5-min walk.", Intervention.BREAK, true), s.coach)
        assertEquals(dashboard, s.toDashboardState(), "the phone rebuilds exactly the PC dashboard")
        val raw = get().body
        assertTrue("\"level\":\"TIRED\"" in raw && "\"kind\":\"BREAK\"" in raw, raw)
    }

    @Test fun notReadyIs503() {
        val e = CompanionEndpoint(code, { null }, log = {})
        assertEquals(503, e.handle("GET", "/state", auth, "x").status)
    }

    @Test fun phoneClampsUntrustedValues() {
        val evil = CompanionState(score = 999, level = Level.OK, reasons = List(50) { "x".repeat(500) }, calibrated = true,
            coach = CompanionCoach("y".repeat(10_000), Intervention.BREAK, false), ts = 0, llmCalls = -5, calibrationMs = 0, windowMs = Long.MAX_VALUE)
        val d = evil.toDashboardState()
        assertEquals(100, d.fatigue.score); assertEquals(5, d.fatigue.reasons.size); assertTrue(d.fatigue.reasons.all { it.length <= 60 })
        assertTrue(d.coach!!.text.length <= 200); assertEquals(0, d.llmCallCount); assertEquals(1_000, d.calibrationMs); assertEquals(600_000, d.windowMs)
    }

    @Test fun pairingInputIsValidated() {
        assertEquals(PairingTarget("192.168.43.20", 8767, "123456"), PairingTarget.parse(" 192.168.43.20 ", "", "123456"))
        assertNull(PairingTarget.parse("evil.com/x?", "8767", "123456"))
        assertNull(PairingTarget.parse("1.2.3.4@evil.com", "8767", "123456"))
        assertNull(PairingTarget.parse("1.2.3.4", "99999", "123456"))
        assertNull(PairingTarget.parse("1.2.3.4", "8767", "12a456"))
        assertNull(PairingTarget.parse("1.2.3.4", "8767", "123"))
    }

    @Test fun logsNeitherCodeNorIp() {
        get(); get("Bearer 000000"); get()
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.none { code in it || "192.168" in it || "000000" in it }, logs.toString())
    }

    @Test fun offUnlessEnvSet() {
        assertNull(CompanionServer.fromEnv({ null }) { null })
        assertNull(CompanionServer.fromEnv({ null }) { if (it == "EG_COMPANION") "0" else null })
        val on = CompanionServer.fromEnv({ null }) { mapOf("EG_COMPANION" to "1", "EG_COMPANION_HOST" to "127.0.0.1", "EG_COMPANION_PORT" to "9123")[it] }!!
        assertEquals(9123, on.port); assertEquals("127.0.0.1", on.lanHost)
        assertTrue(Regex("^[0-9]{6}$").matches(on.endpoint.code))
        assertTrue(CompanionEndpoint.newCode() != CompanionEndpoint.newCode() || CompanionEndpoint.newCode() != CompanionEndpoint.newCode())
    }

    @Test fun realServerAndClientEndToEnd(): Unit = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val server = CompanionServer(CompanionEndpoint(code, { dashboard.toCompanionState(now) }, log = {}), lanHost = null, port = port)
        assertTrue(server.start())
        val client = CompanionClient(CIO)
        val raw = HttpClient(CIO) { expectSuccess = false }
        try {
            val ok = client.fetch(PairingTarget("127.0.0.1", port, code))
            assertIs<CompanionResult.Ok>(ok)
            assertEquals(dashboard, ok.state.toDashboardState())
            assertEquals(CompanionResult.WrongCode, client.fetch(PairingTarget("127.0.0.1", port, "000000")))

            val resp = raw.get("http://127.0.0.1:$port/state") { header("Authorization", auth); header("Origin", "https://evil.com") }
            assertEquals(200, resp.status.value)
            assertNull(resp.headers["Access-Control-Allow-Origin"], "no CORS")
            assertEquals("no-store", resp.headers["Cache-Control"])
            assertEquals(405, raw.options("http://127.0.0.1:$port/state") { header("Origin", "https://evil.com") }.status.value)
            assertEquals(405, raw.post("http://127.0.0.1:$port/state") { header("Authorization", auth) }.status.value)

            val closedPort = ServerSocket(0).use { it.localPort }
            assertIs<CompanionResult.Unreachable>(client.fetch(PairingTarget("127.0.0.1", closedPort, code)))
        } finally {
            client.close(); raw.close(); server.stop()
        }
    }
}
