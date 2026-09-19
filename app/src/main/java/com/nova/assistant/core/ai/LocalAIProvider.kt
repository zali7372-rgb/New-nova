package com.nova.assistant.core.ai

import kotlin.random.Random

/**
 * Fully offline provider. Does not do open-ended generative reasoning - it
 * can't, without a model - but it guarantees NOVA can ALWAYS answer something
 * useful and in-character, even with radios off / no API key configured.
 *
 * This is also what CommandProcessor falls back to for GENERAL_QUESTION intents
 * when no remote provider is available, and it's what powers small talk /
 * personality responses so those never depend on the network at all.
 */
class LocalAIProvider : AIProvider {

    override val id: String = "local"
    override val displayName: String = "Helyi NOVA (offline)"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun generate(request: AIRequest): AIResult {
        val text = buildAnswer(request)
        return AIResult.Success(text, id)
    }

    private fun buildAnswer(request: AIRequest): String {
        val utterance = request.utterance.trim()

        // Try to ground the answer in remembered facts first.
        if (request.relevantMemories.isNotEmpty()) {
            val facts = request.relevantMemories.joinToString("; ")
            return "A jegyzeteim alapján: $facts"
        }

        return when {
            utterance.isBlank() -> pick(FALLBACK_EMPTY)
            utterance.endsWith("?") -> pick(FALLBACK_QUESTION)
            else -> pick(FALLBACK_STATEMENT)
        }
    }

    private fun pick(options: List<String>): String = options[Random.nextInt(options.size)]

    companion object {
        private val FALLBACK_QUESTION = listOf(
            "Ezt nem tudom biztosan offline módban, de ha engedélyezed az internetes AI szolgáltatást, pontosabb választ tudok adni.",
            "Erre a kérdésre most nincs megbízható válaszom helyi tudásból. Szeretnéd, hogy rákeressek az interneten?",
            "Jelenleg csak a helyi AI-t használom, így ez a kérdés meghaladja a tudásomat. Kapcsold be a távoli AI szolgáltatót a Beállításokban a pontosabb válaszokért."
        )
        private val FALLBACK_STATEMENT = listOf(
            "Értem, feljegyeztem magamban.",
            "Rendben, ezt megjegyeztem a beszélgetés kontextusában.",
            "Vettem."
        )
        private val FALLBACK_EMPTY = listOf(
            "Nem hallottalak tisztán, mondanád még egyszer?",
            "Nem érkezett hozzám semmi. Próbáld meg újra."
        )
    }
}
