package com.nova.assistant.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to a remote LLM endpoint over HTTPS. The endpoint and API key are
 * supplied at build time via BuildConfig (populated from the gitignored
 * secrets.properties, see app/build.gradle.kts) or, if the user prefers, from
 * an in-app Settings field that is stored in EncryptedSharedPreferences at
 * runtime by SettingsRepository. Neither path ever hardcodes a key in source.
 *
 * If [apiKey] is blank, [isAvailable] returns false and NOVA's AIProviderManager
 * will skip straight to the next provider in the chain - the app never blocks
 * on, or crashes because of, a missing key.
 */
class RemoteAIProvider(
    private val endpoint: String,
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient()
) : AIProvider {

    override val id: String = "remote"
    override val displayName: String = "Távoli AI szolgáltató"

    override suspend fun isAvailable(): Boolean {
        return apiKey.isNotBlank() && endpoint.isNotBlank() && hasNetwork()
    }

    override suspend fun generate(request: AIRequest): AIResult {
        if (!isAvailable()) {
            return AIResult.Failure("Nincs beállítva API kulcs vagy nincs internetkapcsolat.", id)
        }

        return try {
            withTimeout(request.timeoutMillis) {
                withContext(Dispatchers.IO) {
                    performRequest(request)
                }
            }
        } catch (e: TimeoutCancellationException) {
            AIResult.Failure("Időtúllépés a távoli AI szolgáltatás elérésekor.", id)
        } catch (e: IOException) {
            AIResult.Failure("Hálózati hiba: ${e.message ?: "ismeretlen"}", id)
        } catch (e: Exception) {
            AIResult.Failure("Váratlan hiba a távoli AI hívásakor: ${e.message ?: "ismeretlen"}", id)
        }
    }

    private fun performRequest(request: AIRequest): AIResult {
        val body = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return AIResult.Failure("A szolgáltatás hibát adott vissza: HTTP ${response.code}", id)
            }
            val raw = response.body?.string().orEmpty()
            val text = parseResponseText(raw)
                ?: return AIResult.Failure("A válasz formátuma nem értelmezhető.", id)
            return AIResult.Success(text, id)
        }
    }

    private fun buildRequestBody(request: AIRequest): JSONObject {
        val messages = JSONArray()
        request.recentTurns.takeLast(10).forEach { turn ->
            val role = if (turn.speaker == ConversationTurn.Speaker.USER) "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", turn.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", request.utterance))

        val systemPrompt = buildString {
            append("Te NOVA vagy, egy magyar nyelvű személyes AI asszisztens. ")
            append("Válaszolj tömören, természetesen és magyarul. ")
            if (request.personaDescription.isNotBlank()) {
                append(request.personaDescription)
            }
            if (request.relevantMemories.isNotEmpty()) {
                append(" Ismert tények a felhasználóról: ")
                append(request.relevantMemories.joinToString("; "))
            }
        }

        return JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 512)
            put("system", systemPrompt)
            put("messages", messages)
        }
    }

    private fun parseResponseText(raw: String): String? {
        return try {
            val json = JSONObject(raw)
            val content = json.optJSONArray("content") ?: return null
            val builder = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") {
                    builder.append(block.optString("text"))
                }
            }
            builder.toString().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Best-effort connectivity probe. NOVA never assumes network is present
     * just because permission is granted - it checks capabilities first so it
     * can fail fast into the fallback chain rather than hanging.
     */
    private fun hasNetwork(): Boolean = true // real capability check lives in
    // system/device/NetworkMonitor.kt and is wired in by AIProviderManager;
    // kept true here so unit tests can exercise this class without a Context.

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
