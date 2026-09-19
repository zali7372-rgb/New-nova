package com.nova.assistant.core.intent

/** The set of things NOVA can recognize a user utterance as wanting. */
enum class NovaIntent {
    OPEN_APP,
    SEARCH_WEB,
    OPEN_WEBPAGE,
    MEMORY_SAVE,
    MEMORY_READ,
    MEMORY_FORGET,
    SETTINGS,
    DEVICE_ACTION,       // Wi-Fi, Bluetooth, camera, etc.
    WEATHER,
    TIME,
    GENERAL_QUESTION,
    CONVERSATION,        // follow-up / continuation of previous turn
    HELP,
    GREETING,
    UNKNOWN
}

/**
 * Result of classifying an utterance: the winning intent, a confidence score
 * used for logging/testing, and any slots (extracted arguments) the matching
 * rule captured - e.g. the app name for OPEN_APP, or the fact text for
 * MEMORY_SAVE.
 */
data class IntentResult(
    val intent: NovaIntent,
    val confidence: Float,
    val slots: Map<String, String> = emptyMap(),
    val originalUtterance: String
)
