package com.nova.assistant.ui.main

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)
