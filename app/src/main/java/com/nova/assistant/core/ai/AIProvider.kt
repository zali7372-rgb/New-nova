package com.nova.assistant.core.ai

/**
 * Abstraction over "something that can turn a user's utterance plus context
 * into a natural-language reply". NOVA can swap providers at runtime without
 * any other part of the system knowing or caring which one answered.
 *
 * Implementations MUST NOT throw. Failures are reported as [AIResult.Failure]
 * so the caller (AIProviderManager) can fall back to the next provider.
 */
interface AIProvider {

    /** Stable identifier, also used as the user-facing "AI provider" setting value. */
    val id: String

    /** Human readable Hungarian name shown in Settings. */
    val displayName: String

    /**
     * Cheap, synchronous-ish check for "can this provider realistically answer
     * right now". E.g. RemoteAIProvider checks whether an API key is configured
     * and the network is reachable; LocalAIProvider always returns true.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Produce a reply for [request]. Implementations should respect
     * [AIRequest.timeoutMillis] internally where relevant (e.g. network calls).
     */
    suspend fun generate(request: AIRequest): AIResult
}

/**
 * Everything a provider needs to produce a grounded answer: the raw utterance,
 * recent turns for context, and the resolved user memory that's relevant.
 */
data class AIRequest(
    val utterance: String,
    val recentTurns: List<ConversationTurn>,
    val relevantMemories: List<String> = emptyList(),
    val personaDescription: String = "",
    val timeoutMillis: Long = 8000L
)

data class ConversationTurn(
    val speaker: Speaker,
    val text: String,
    val timestampMillis: Long
) {
    enum class Speaker { USER, NOVA }
}

sealed class AIResult {
    data class Success(val text: String, val providerId: String) : AIResult()
    data class Failure(val reason: String, val providerId: String) : AIResult()
}
