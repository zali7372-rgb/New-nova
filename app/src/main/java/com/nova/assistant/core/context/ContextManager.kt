package com.nova.assistant.core.context

import com.nova.assistant.core.ai.ConversationTurn
import com.nova.assistant.core.intent.NovaIntent

/**
 * Keeps a rolling window of recent conversation turns in memory (per-process,
 * cleared on process death by design - long-term facts belong in MemoryManager,
 * not here) and can resolve short follow-ups like "És Szegeden?" against the
 * last topic NOVA was discussing.
 */
class ContextManager(private val maxTurns: Int = 20) {

    private val turns = ArrayDeque<ConversationTurn>()
    private var lastTopic: Topic? = null

    fun recordUserTurn(text: String) {
        push(ConversationTurn(ConversationTurn.Speaker.USER, text, System.currentTimeMillis()))
    }

    fun recordNovaTurn(text: String) {
        push(ConversationTurn(ConversationTurn.Speaker.NOVA, text, System.currentTimeMillis()))
    }

    fun recentTurns(): List<ConversationTurn> = turns.toList()

    fun hasPendingContext(): Boolean = lastTopic != null

    fun setTopic(intent: NovaIntent, slots: Map<String, String>) {
        lastTopic = Topic(intent, slots)
    }

    fun clearTopic() {
        lastTopic = null
    }

    /**
     * Merge a follow-up utterance's slots with the last topic's slots so, e.g.,
     * a WEATHER query for "Szeged" inherits nothing but a bare "És Szegeden?"
     * (classified as CONVERSATION with a location slot) inherits intent=WEATHER.
     */
    fun resolveFollowUp(newSlots: Map<String, String>): Topic? {
        val topic = lastTopic ?: return null
        return topic.copy(slots = topic.slots + newSlots)
    }

    fun previousUserUtterance(): String? =
        turns.lastOrNull { it.speaker == ConversationTurn.Speaker.USER }?.text

    private fun push(turn: ConversationTurn) {
        turns.addLast(turn)
        while (turns.size > maxTurns) turns.removeFirst()
    }

    data class Topic(val intent: NovaIntent, val slots: Map<String, String>)
}
