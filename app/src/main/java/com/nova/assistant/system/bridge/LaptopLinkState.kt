package com.nova.assistant.system.bridge

/** Mirrors NOVA Desktop's network/protocol.py message shapes on the Kotlin side. */
sealed class LaptopLinkState {
    data object Disconnected : LaptopLinkState()
    data object Connecting : LaptopLinkState()
    data class Connected(val deviceLabel: String) : LaptopLinkState()
    data class Error(val message: String) : LaptopLinkState()
}

/** What NOVA Desktop sent back for one utterance. */
data class LaptopReply(val text: String)
