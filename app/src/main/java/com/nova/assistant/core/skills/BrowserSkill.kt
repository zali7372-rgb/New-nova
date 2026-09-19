package com.nova.assistant.core.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent

class BrowserSkill(private val context: Context) : Skill {
    override val name = "BrowserSkill"
    override val description = "Weboldalak megnyitása böngészőben."
    override val supportedIntents = setOf(NovaIntent.OPEN_WEBPAGE)
    override val requiredPermissions: Set<String> = setOf(android.Manifest.permission.INTERNET)

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        val url = intentResult.slots["url"] ?: DEFAULT_URL
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SkillResult("Megnyitottam a böngészőt.")
        } catch (e: Exception) {
            SkillResult("Nem sikerült megnyitni a böngészőt: ${e.message ?: "ismeretlen hiba"}")
        }
    }

    companion object {
        private const val DEFAULT_URL = "https://www.google.com"
    }
}
