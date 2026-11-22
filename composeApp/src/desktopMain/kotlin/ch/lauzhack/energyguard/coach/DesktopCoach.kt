package ch.lauzhack.energyguard.coach

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The desktop's OpenRouter client, or why there is none ([unavailable] = "no key" / "EG_COACH=offline").
 * Built once at start; the Profile "AI coach" switch plugs it into the coach or unplugs it (FrugalEnergyCoach.setLlm).
 */
class DesktopLlm(val client: LlmClient?, val unavailable: String?)

/** OPENROUTER_API_KEY (env var or .env) -> OpenRouter client; EG_COACH=offline forces none, EG_COACH_MODEL overrides the model. */
fun desktopLlm(): DesktopLlm {
    val key = envOrDotEnv("OPENROUTER_API_KEY")?.takeIf { it.isNotBlank() } // never printed
    if (System.getenv("EG_COACH") == "offline") return DesktopLlm(null, "EG_COACH=offline")
    if (key == null) return DesktopLlm(null, "no key")
    val model = System.getenv("EG_COACH_MODEL")?.takeIf { it.isNotBlank() } ?: OpenRouterLlmClient.DEFAULT_MODEL
    println("[coach] OpenRouter available ($model), max 1 call / 10 min")
    return DesktopLlm(OpenRouterLlmClient(HttpClient(CIO) { engine { requestTimeout = 5_000 } }, key, model), null)
}

/** Desktop EnergyCoach, offline templates until [FrugalEnergyCoach.setLlm] plugs a client in (main.kt, from Settings). */
fun desktopEnergyCoach(): FrugalEnergyCoach =
    FrugalEnergyCoach(llm = null, clock = System::currentTimeMillis, dispatcher = Dispatchers.IO)

fun localTimeHHmm(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

/** Env var first, then KEY=VALUE in .env of the working dir or its parent (gradle run starts in composeApp/). */
private fun envOrDotEnv(name: String): String? {
    System.getenv(name)?.let { return it }
    val cwd = File("").absoluteFile
    return listOfNotNull(cwd, cwd.parentFile).map { File(it, ".env") }.firstOrNull { it.isFile }
        ?.readLines()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.substringBefore(" #")
        ?.trim()
        ?.removeSurrounding("\"")
}
