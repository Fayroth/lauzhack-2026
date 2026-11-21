package ch.lauzhack.energyguard.coach

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * OpenRouter chat completion (https://openrouter.ai/docs). The engine (CIO on desktop) is chosen by the caller.
 * [apiKey] comes from OPENROUTER_API_KEY in .env: never logged, never put in an exception message.
 */
class OpenRouterLlmClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : LlmClient {
    override suspend fun complete(system: String, user: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 60)
            put("temperature", 0.6)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
        }
        val response = http.post(URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("X-Title", "EnergyGuard")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (!response.status.isSuccess()) error("OpenRouter HTTP ${response.status.value}")
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]!!
            .jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }

    companion object {
        const val URL = "https://openrouter.ai/api/v1/chat/completions"
        const val DEFAULT_MODEL = "anthropic/claude-haiku-5.5" // small, cheap, fast; override with EG_COACH_MODEL
    }
}
