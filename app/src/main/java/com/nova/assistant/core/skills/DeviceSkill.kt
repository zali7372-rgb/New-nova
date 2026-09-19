package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import com.nova.assistant.system.device.DeviceActionManager
import com.nova.assistant.system.device.DeviceActionResult

class DeviceSkill(private val deviceActionManager: DeviceActionManager) : Skill {
    override val name = "DeviceSkill"
    override val description = "Telefon funkciók és beállítási képernyők megnyitása."
    override val supportedIntents = setOf(NovaIntent.DEVICE_ACTION)
    override val requiredPermissions: Set<String> = emptySet()

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        val action = intentResult.slots["action"]
            ?: return SkillResult("Nem tudom, melyik funkciót szeretnéd megnyitni.")

        return when (val result = deviceActionManager.execute(action)) {
            is DeviceActionResult.Success -> SkillResult(result.spokenConfirmation)
            is DeviceActionResult.Unsupported -> SkillResult(result.reason)
        }
    }
}
