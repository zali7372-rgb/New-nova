package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent

/**
 * Doesn't launch an Activity directly (Skills are Context-light, testable
 * plain classes). Instead it returns an instruction the ViewModel/UI layer
 * observes and acts on, keeping navigation in the UI layer where it belongs.
 */
class SettingsSkill(private val onOpenSettingsRequested: () -> Unit) : Skill {
    override val name = "SettingsSkill"
    override val description = "A NOVA beállítások képernyő megnyitása."
    override val supportedIntents = setOf(NovaIntent.SETTINGS)
    override val requiredPermissions: Set<String> = emptySet()

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        onOpenSettingsRequested()
        return SkillResult("Megnyitom a beállításokat.")
    }
}
