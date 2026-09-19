package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent

data class SkillResult(
    val spokenResponse: String,
    val shouldSpeak: Boolean = true
)

/**
 * A self-contained capability NOVA has. SkillManager picks the right skill
 * for a classified intent and delegates execution to it. Each skill declares
 * which intents it supports and which Android permissions it needs so the UI
 * (Settings -> About / permission rationale) can explain itself accurately.
 */
interface Skill {
    val name: String
    val description: String
    val supportedIntents: Set<NovaIntent>
    val requiredPermissions: Set<String>

    suspend fun execute(intentResult: IntentResult): SkillResult
}
