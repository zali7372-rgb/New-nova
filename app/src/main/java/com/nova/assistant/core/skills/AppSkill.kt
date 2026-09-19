package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import com.nova.assistant.system.apps.AppManager
import com.nova.assistant.system.apps.LaunchResult

class AppSkill(private val appManager: AppManager) : Skill {
    override val name = "AppSkill"
    override val description = "Telepített alkalmazások megnyitása/indítása."
    override val supportedIntents = setOf(NovaIntent.OPEN_APP)
    override val requiredPermissions: Set<String> = emptySet()

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        val appName = intentResult.slots["appName"]
        if (appName.isNullOrBlank()) {
            return SkillResult("Nem értettem, melyik alkalmazást szeretnéd megnyitni.")
        }

        return when (val result = appManager.launch(appName)) {
            is LaunchResult.Success -> SkillResult("Megnyitottam: ${result.app.label}")
            is LaunchResult.NotFound -> {
                val suggestions = result.suggestions.joinToString(", ") { it.label }
                if (suggestions.isBlank()) {
                    SkillResult("Nem találtam a telepített alkalmazások között ilyet: „$appName”.")
                } else {
                    SkillResult("Nem találtam pontosan „$appName” nevű alkalmazást. Talán ezekre gondoltál: $suggestions")
                }
            }
            is LaunchResult.Error -> SkillResult(result.message)
        }
    }
}
