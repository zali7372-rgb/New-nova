package com.nova.assistant.core.command

import com.nova.assistant.core.context.ContextManager
import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.response.ResponseGenerator
import com.nova.assistant.core.skills.SkillManager
import com.nova.assistant.core.skills.SkillResult

class CommandProcessor(
    private val skillManager: SkillManager,
    private val contextManager: ContextManager,
    private val responseGenerator: ResponseGenerator
) {
    suspend fun process(steps: List<IntentResult>): SkillResult {
        val results = mutableListOf<SkillResult>()

        for (step in steps) {
            val resolvedStep = resolveAgainstContext(step)
            val result = skillManager.handle(resolvedStep)
            results.add(result)
            contextManager.setTopic(resolvedStep.intent, resolvedStep.slots)
        }

        return responseGenerator.combine(results)
    }

    /**
     * If this step is a bare CONVERSATION follow-up (e.g. "És Szegeden?"),
     * merge it against the last topic so it actually becomes e.g. a WEATHER
     * query for the new city rather than an unresolved general question.
     */
    private fun resolveAgainstContext(step: IntentResult): IntentResult {
        if (step.intent != com.nova.assistant.core.intent.NovaIntent.CONVERSATION) return step
        val merged = contextManager.resolveFollowUp(step.slots) ?: return step
        return step.copy(intent = merged.intent, slots = merged.slots)
    }
}
