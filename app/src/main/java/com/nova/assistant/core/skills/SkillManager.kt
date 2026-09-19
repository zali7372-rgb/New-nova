package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent

/**
 * Holds every registered [Skill] and routes a classified intent to whichever
 * skill declares support for it. Adding a new capability to NOVA means
 * writing a Skill and registering it here - nothing else in the pipeline
 * needs to change (open/closed by design).
 */
class SkillManager(skills: List<Skill>) {

    private val byIntent: Map<NovaIntent, Skill> = buildMap {
        for (skill in skills) {
            for (intent in skill.supportedIntents) {
                put(intent, skill)
            }
        }
    }

    fun allSkills(): List<Skill> = byIntent.values.distinct()

    suspend fun handle(intentResult: IntentResult): SkillResult {
        val skill = byIntent[intentResult.intent]
            ?: return SkillResult("Nem tudom, hogyan kezeljem ezt a kérést.")
        return try {
            skill.execute(intentResult)
        } catch (e: Exception) {
            SkillResult(
                "Hiba történt a(z) ${skill.name} végrehajtása közben: ${e.message ?: "ismeretlen hiba"}"
            )
        }
    }
}
