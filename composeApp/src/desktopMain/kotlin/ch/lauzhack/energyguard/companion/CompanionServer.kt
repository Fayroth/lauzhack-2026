package ch.lauzhack.energyguard.companion

import ch.lauzhack.energyguard.guard.Reply
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
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure request handling for the phone companion's `GET /state` (#48, contract + security rules in docs/API.md).
 * READ-ONLY: [snapshot] is the only thing it touches. Code on every request, constant-time compare, per-IP lockout.
 * Logs nothing but a request counter (no IP, no code).
 */
class CompanionEndpoint(
    val code: String,
    private val snapshot: () -> CompanionState?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = ::println,
) {
    private val expected = "Bearer $code".toByteArray()
    private val failures = HashMap<String, MutableList<Long>>() // remote address -> recent wrong-code times
    private val lockedUntil = HashMap<String, Long>()
    val requestCount = AtomicInteger()
    val rejectedCount = AtomicInteger()

    fun handle(method: String, path: String, authorization: String?, remote: String): Reply {
        if (path != "/state") return error(404, "not found")
        if (method != "GET") return error(405, "method not allowed")
        val now = clock()
        synchronized(this) {
            if ((lockedUntil[remote] ?: 0) > now) return error(429, "too many wrong codes")
            val given = authorization?.toByteArray() ?: ByteArray(0)
            if (!MessageDigest.isEqual(expected, given)) {
                val recent = failures.getOrPut(remote) { mutableListOf() }.apply { removeAll { now - it > WINDOW_MS }; add(now) }
                if (recent.size >= MAX_FAILURES) { lockedUntil[remote] = now + LOCK_MS; recent.clear() }
                if (failures.size > MAX_TRACKED) failures.keys.retainAll(setOf(remote)) // bounded memory
                val n = rejectedCount.incrementAndGet()
                log("[companion] wrong code (#$n)")
                return error(401, "wrong code")
            }
            failures.remove(remote)
            lockedUntil.remove(remote)
        }
        val state = runCatching { snapshot() }.getOrNull() ?: return error(503, "not ready")
        val n = requestCount.incrementAndGet()
        if (n == 1) log("[companion] phone connected") else if (n % 300 == 0) log("[companion] $n states served")
        return Reply(200, CompanionJson.encodeToString(CompanionState.serializer(), state))
    }

    private fun error(status: Int, message: String) = Reply(status, """{"error":"$message"}""")

    companion object {
        const val MAX_FAILURES = 5
        const val WINDOW_MS = 60_000L
        const val LOCK_MS = 60_000L
        private const val MAX_TRACKED = 256
        private val rng = SecureRandom()

        /** 6 random digits, new at every app start. */
        fun newCode(): String = "%06d".format(rng.nextInt(1_000_000))
    }
}

/** Thin Ktor CIO wrapper around [CompanionEndpoint]: one listener per host (LAN IP + 127.0.0.1 for adb reverse). */
class CompanionServer(val endpoint: CompanionEndpoint, val lanHost: String?, val port: Int) {
    private val servers = mutableListOf<EmbeddedServer<*, *>>()

    /** What the dashboard shows, e.g. "Phone: 192.168.43.20:8767 · code 123456". */
    val pairingLabel: String
        get() = "Phone: ${lanHost ?: "no Wi-Fi address"}:$port · code ${endpoint.code}"

    /** Never throws: a busy port or a missing LAN address only means the phone shows "Not connected". */
    fun start(): Boolean {
        for (host in listOfNotNull(lanHost, LOOPBACK).distinct()) {
            if (!ch.lauzhack.energyguard.guard.portFree(host, port)) {
                System.err.println("[companion] port $port is busy on $host: is another EnergyGuard window still open?")
                continue
            }
            try {
                servers += embeddedServer(CIO, host = host, port = port) {
                    intercept(ApplicationCallPipeline.Call) {
                        val req = call.request
                        val reply = endpoint.handle(
                            method = req.httpMethod.value,
                            path = req.path(),
                            authorization = req.headers[HttpHeaders.Authorization],
                            remote = req.local.remoteAddress,
                        )
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.respondText(reply.body, ContentType.Application.Json, HttpStatusCode.fromValue(reply.status))
                        finish()
                    }
                }.start(wait = false)
                println("[companion] read-only phone endpoint on http://$host:$port/state")
            } catch (e: Exception) {
                System.err.println("[companion] could not listen on $host:$port (${e.javaClass.simpleName})")
            }
        }
        if (servers.isNotEmpty()) { // ASCII only: the Windows console mangles "·"
            println("[companion] Phone: ${lanHost ?: "no Wi-Fi address"}:$port  code ${endpoint.code}  (Windows Firewall: allow on Private networks only)")
        }
        return servers.isNotEmpty()
    }

    fun stop() {
        servers.forEach { it.stop(gracePeriodMillis = 100, timeoutMillis = 500) }
        servers.clear()
    }

    companion object {
        const val LOOPBACK = "127.0.0.1"

        /** null unless EG_COMPANION=1: without it the desktop app behaves exactly as before (nothing more listens). */
        fun fromEnv(snapshot: () -> CompanionState?, env: (String) -> String? = System::getenv): CompanionServer? =
            if (env("EG_COMPANION") == "1") create(snapshot, env) else null

        /** true when the app should start with phone sharing on (EG_COMPANION=1). The Profile switch changes it at runtime. */
        fun enabledByEnv(env: (String) -> String? = System::getenv): Boolean = env("EG_COMPANION") == "1"

        /**
         * A new server (not started) with a NEW random code: the Profile "Phone sharing" switch builds one each time it is
         * turned on, so an old code never works again. Port / host from EG_COMPANION_PORT / EG_COMPANION_HOST.
         */
        fun create(snapshot: () -> CompanionState?, env: (String) -> String? = System::getenv): CompanionServer {
            val port = env("EG_COMPANION_PORT")?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: PairingTarget.DEFAULT_PORT
            val host = env("EG_COMPANION_HOST")?.trim()?.takeIf { it.isNotEmpty() } ?: lanIpv4()
            return CompanionServer(CompanionEndpoint(CompanionEndpoint.newCode(), snapshot), host, port)
        }

        /**
         * The PC's address on the Wi-Fi (e.g. the phone's hotspot). First the default-route interface (a UDP "connect"
         * only picks a route, no packet is sent), else the first private IPv4 of a real, up interface.
         */
        fun lanIpv4(): String? {
            runCatching {
                DatagramSocket().use { s ->
                    s.connect(InetAddress.getByName("192.0.2.1"), 9) // TEST-NET-1: never contacted
                    (s.localAddress as? Inet4Address)?.takeIf { it.isSiteLocalAddress }?.let { return it.hostAddress }
                }
            }
            val virtual = Regex("virtual|vethernet|hyper-v|vmware|vbox|wsl|docker|vpn|tap|tun|bluetooth", RegexOption.IGNORE_CASE)
            return runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint && !virtual.containsMatchIn(it.displayName + it.name) }
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
            }.getOrNull()
        }
    }
}
