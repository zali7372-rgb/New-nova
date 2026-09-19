package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import com.nova.assistant.core.memory.MemoryManager

class MemorySkill(private val memoryManager: MemoryManager) : Skill {
    override val name = "MemorySkill"
    override val description = "Tények és preferenciák megjegyzése, felidézése és törlése."
    override val supportedIntents = setOf(
        NovaIntent.MEMORY_SAVE,
        NovaIntent.MEMORY_READ,
        NovaIntent.MEMORY_FORGET
    )
    override val requiredPermissions: Set<String> = emptySet()

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        return when (intentResult.intent) {
            NovaIntent.MEMORY_SAVE -> {
                val fact = intentResult.slots["fact"] ?: intentResult.originalUtterance
                SkillResult(memoryManager.remember(fact))
            }
            NovaIntent.MEMORY_READ -> {
                SkillResult(memoryManager.recall(intentResult.originalUtterance))
            }
            NovaIntent.MEMORY_FORGET -> {
                SkillResult(memoryManager.forgetMostRecent())
            }
            else -> SkillResult("Ezt a memóriaműveletet nem ismerem fel.")
        }
    }
}
