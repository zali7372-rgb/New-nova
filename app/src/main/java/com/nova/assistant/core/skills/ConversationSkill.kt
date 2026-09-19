package com.nova.assistant.core.skills

import com.nova.assistant.core.ai.AIProviderManager
import com.nova.assistant.core.ai.AIRequest
import com.nova.assistant.core.ai.AIResult
import com.nova.assistant.core.context.ContextManager
import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import com.nova.assistant.core.memory.MemoryManager
import com.nova.assistant.core.personality.PersonalityManager

/**
 * Catch-all conversational skill: greetings, "what can you do", follow-ups,
 * and open-ended questions all route here and get answered through the
 * AIProviderManager chain (which itself always degrades gracefully).
 */
class ConversationSkill(
    private val aiProviderManager: AIProviderManager,
    private val contextManager: ContextManager,
    private val memoryManager: MemoryManager,
    private val personalityManager: PersonalityManager
) : Skill {
    override val name = "ConversationSkill"
    override val description = "Általános beszélgetés, köszönések és nyitott kérdések kezelése."
    override val supportedIntents = setOf(
        NovaIntent.GENERAL_QUESTION,
        NovaIntent.CONVERSATION,
        NovaIntent.GREETING,
        NovaIntent.HELP
    )
    override val requiredPermissions: Set<String> = emptySet()

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        return when (intentResult.intent) {
            NovaIntent.GREETING -> SkillResult(personalityManager.greeting())
            NovaIntent.HELP -> SkillResult(personalityManager.helpText())
            else -> answerViaAI(intentResult)
        }
    }

    private suspend fun answerViaAI(intentResult: IntentResult): SkillResult {
        val relevantMemories = memoryManager.relevantFactsFor(intentResult.originalUtterance)
        val request = AIRequest(
            utterance = intentResult.originalUtterance,
            recentTurns = contextManager.recentTurns(),
            relevantMemories = relevantMemories,
            personaDescription = personalityManager.systemPersonaDescription()
        )
        return when (val result = aiProviderManager.answer(request)) {
            is AIResult.Success -> SkillResult(result.text)
            is AIResult.Failure -> SkillResult(
                "Jelenleg nem érem el az AI szolgáltatást. (${result.reason})"
            )
        }
    }
}
