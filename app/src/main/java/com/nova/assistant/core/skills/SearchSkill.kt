package com.nova.assistant.core.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import java.net.URLEncoder

/**
 * Honesty constraint from the spec: NOVA must never claim to have searched
 * the internet when it hasn't. This skill's actual capability is opening a
 * real search-results page in the browser for the user's query - it does NOT
 * fabricate search results itself. The spoken confirmation reflects exactly
 * that ("megnyitottam a keresést"), never "megtaláltam, hogy...".
 */
class SearchSkill(private val context: Context) : Skill {
    override val name = "SearchSkill"
    override val description = "Valódi internetes keresés indítása a böngészőben."
    override val supportedIntents = setOf(NovaIntent.SEARCH_WEB)
    override val requiredPermissions: Set<String> = setOf(android.Manifest.permission.INTERNET)

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        val query = intentResult.slots["query"]?.trim()
        if (query.isNullOrBlank()) {
            return SkillResult("Mit keressek az interneten?")
        }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val uri = Uri.parse("https://www.google.com/search?q=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SkillResult("Megnyitottam a keresést a böngészőben: „$query”")
        } catch (e: Exception) {
            SkillResult("Nem sikerült elindítani a keresést: ${e.message ?: "ismeretlen hiba"}")
        }
    }
}
