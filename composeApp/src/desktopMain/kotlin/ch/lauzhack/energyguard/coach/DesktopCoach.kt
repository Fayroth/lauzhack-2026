package ch.lauzhack.energyguard.coach

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Desktop EnergyCoach: OpenRouter when OPENROUTER_API_KEY is set (env var or .env), offline templates otherwise.
 * EG_COACH=offline forces templates, EG_COACH_MODEL overrides the model. The key is never printed.
 */
fun desktopEnergyCoach(): FrugalEnergyCoach {
    val key = envOrDotEnv("OPENROUTER_API_KEY")?.takeIf { it.isNotBlank() }
    val offline = System.getenv("EG_COACH") == "offline"
    val llm = if (key != null && !offline) {
        val model = System.getenv("EG_COACH_MODEL")?.takeIf { it.isNotBlank() } ?: OpenRouterLlmClient.DEFAULT_MODEL
        println("[coach] OpenRouter on ($model), max 1 call / 10 min")
        OpenRouterLlmClient(HttpClient(CIO) { engine { requestTimeout = 5_000 } }, key, model)
    } else {
        println("[coach] offline templates (${if (offline) "EG_COACH=offline" else "no OPENROUTER_API_KEY"})")
        null
    }
    return FrugalEnergyCoach(llm, clock = System::currentTimeMillis, dispatcher = Dispatchers.IO)
}

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
