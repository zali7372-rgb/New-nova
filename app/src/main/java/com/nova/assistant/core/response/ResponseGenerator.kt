package com.nova.assistant.core.response

import com.nova.assistant.core.skills.SkillResult

/**
 * Turns one or more [SkillResult]s (a Planner might produce several for a
 * chained command) into the single string NOVA actually shows/speaks.
 */
class ResponseGenerator {

    fun combine(results: List<SkillResult>): SkillResult {
        if (results.isEmpty()) {
            return SkillResult("Nem sikerült választ generálnom.")
        }
        if (results.size == 1) return results.first()

        val combinedText = results.joinToString(" ") { it.spokenResponse }
        val shouldSpeak = results.any { it.shouldSpeak }
        return SkillResult(combinedText, shouldSpeak)
    }
}
