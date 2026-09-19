package com.nova.assistant.core.planner

import com.nova.assistant.core.intent.IntentEngine
import com.nova.assistant.core.intent.IntentResult

/**
 * Most utterances are single-step ("nyisd meg a Youtube-ot"), but NOVA should
 * still behave sensibly for chained requests like "nyisd meg a Chrome-ot és
 * keress rá a Nova asszisztensre". Planner splits on common Hungarian
 * conjunctions/step separators and classifies each piece independently, so
 * CommandProcessor can execute them in order.
 */
class Planner(private val intentEngine: IntentEngine) {

    fun plan(utterance: String, hasPendingContext: Boolean): List<IntentResult> {
        val steps = splitSteps(utterance)
        if (steps.size <= 1) {
            return listOf(intentEngine.classify(utterance, hasPendingContext))
        }
        return steps.map { intentEngine.classify(it, hasPendingContext = false) }
    }

    private fun splitSteps(utterance: String): List<String> {
        val separators = Regex("(?i)\\s+(és utána|és most|majd|és)\\s+")
        return utterance.split(separators)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
