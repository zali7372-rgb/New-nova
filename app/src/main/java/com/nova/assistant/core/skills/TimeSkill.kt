package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeSkill : Skill {
    override val name = "TimeSkill"
    override val description = "Aktuális idő és dátum lekérdezése."
    override val supportedIntents = setOf(NovaIntent.TIME)
    override val requiredPermissions: Set<String> = emptySet()

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        val formatter = SimpleDateFormat("HH:mm", Locale("hu"))
        val time = formatter.format(Date())
        return SkillResult("Most $time van.")
    }
}
