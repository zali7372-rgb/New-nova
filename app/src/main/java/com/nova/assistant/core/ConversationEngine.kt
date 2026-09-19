package com.nova.assistant.core

import com.nova.assistant.core.command.CommandProcessor
import com.nova.assistant.core.context.ContextManager
import com.nova.assistant.core.planner.Planner
import com.nova.assistant.core.skills.SkillResult

/**
 * NOVA's single front door: UI/VoiceManager call [handleUtterance] with raw
 * recognized text and get back the final response to display/speak. Internally
 * this is: Planner -> IntentEngine (inside Planner) -> CommandProcessor ->
 * SkillManager -> individual Skill -> ResponseGenerator.
 */
class ConversationEngine(
    private val planner: Planner,
    private val commandProcessor: CommandProcessor,
    private val contextManager: ContextManager
) {
    suspend fun handleUtterance(rawUtterance: String): SkillResult {
        contextManager.recordUserTurn(rawUtterance)

        val steps = planner.plan(rawUtterance, contextManager.hasPendingContext())
        val result = commandProcessor.process(steps)

        contextManager.recordNovaTurn(result.spokenResponse)
        return result
    }
}
