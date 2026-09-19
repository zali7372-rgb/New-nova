package com.nova.assistant.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.assistant.NovaApplication
import com.nova.assistant.core.ConversationEngine
import com.nova.assistant.core.personality.PersonalityManager
import com.nova.assistant.data.repository.SettingsRepository
import com.nova.assistant.system.bridge.LaptopBridgeManager
import com.nova.assistant.system.bridge.LaptopLinkState
import com.nova.assistant.system.voice.VoiceManager
import com.nova.assistant.system.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NovaStatus { ONLINE, LISTENING, THINKING, SPEAKING, OFFLINE }

data class MainUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: NovaStatus = NovaStatus.ONLINE,
    val voiceAvailable: Boolean = true,
    val laptopConnected: Boolean = false
)

class MainViewModel(
    private val conversationEngine: ConversationEngine,
    private val voiceManager: VoiceManager,
    private val personalityManager: PersonalityManager,
    private val settingsRepository: SettingsRepository,
    private val laptopBridgeManager: LaptopBridgeManager,
    private val recognizedSpeech: kotlinx.coroutines.flow.SharedFlow<String>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            voiceManager.state.collect { voiceState -> applyVoiceState(voiceState) }
        }
        recognizedSpeech?.let { flow ->
            viewModelScope.launch {
                flow.collect { text -> onTextSubmitted(text) }
            }
        }
        viewModelScope.launch {
            laptopBridgeManager.state.collect { linkState ->
                _uiState.value = _uiState.value.copy(laptopConnected = linkState is LaptopLinkState.Connected)
            }
        }
        // The laptop's reply arrives asynchronously over the WebSocket, on
        // its own timeline - not as a return value of onTextSubmitted() -
        // so it's appended to the chat here, whenever it shows up.
        viewModelScope.launch {
            laptopBridgeManager.replies.collect { reply ->
                appendNovaMessage(reply.text)
                _uiState.value = _uiState.value.copy(status = NovaStatus.ONLINE)
                if (settingsRepository.ttsEnabled) {
                    voiceManager.speak(reply.text)
                }
            }
        }
    }

    fun onMicTapped() {
        if (!settingsRepository.voiceEnabled) {
            appendNovaMessage("A hangvezérlés ki van kapcsolva a Beállításokban.")
            return
        }
        voiceManager.setContinuousModeEnabled(settingsRepository.continuousListeningEnabled)
        voiceManager.startListening()
    }

    fun onTextSubmitted(text: String) {
        if (text.isBlank()) return
        appendUserMessage(text)

        val routeToLaptop = settingsRepository.laptopModeEnabled && laptopBridgeManager.isConnected()
        if (routeToLaptop) {
            _uiState.value = _uiState.value.copy(status = NovaStatus.THINKING)
            laptopBridgeManager.sendTranscript(text)
            // No local reply here - laptopBridgeManager.replies (collected in
            // init) delivers NOVA Desktop's answer once the laptop responds.
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = NovaStatus.THINKING)
            val result = conversationEngine.handleUtterance(text)
            appendNovaMessage(result.spokenResponse)
            _uiState.value = _uiState.value.copy(status = NovaStatus.ONLINE)
            if (result.shouldSpeak && settingsRepository.ttsEnabled) {
                voiceManager.speak(result.spokenResponse)
            }
        }
    }

    private fun appendUserMessage(text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage(text, fromUser = true)
        )
    }

    private fun appendNovaMessage(text: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage(text, fromUser = false)
        )
    }

    private fun applyVoiceState(voiceState: VoiceState) {
        val status = when (voiceState) {
            VoiceState.LISTENING, VoiceState.LISTENING_FOR_WAKE -> NovaStatus.LISTENING
            VoiceState.THINKING -> NovaStatus.THINKING
            VoiceState.SPEAKING -> NovaStatus.SPEAKING
            VoiceState.ERROR, VoiceState.UNAVAILABLE -> NovaStatus.OFFLINE
            VoiceState.IDLE -> NovaStatus.ONLINE
        }
        _uiState.value = _uiState.value.copy(
            status = status,
            voiceAvailable = voiceState != VoiceState.UNAVAILABLE
        )
    }

    class Factory(private val app: NovaApplication) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                app.conversationEngine,
                app.voiceManager,
                app.personalityManager,
                app.settingsRepository,
                app.laptopBridgeManager,
                app.recognizedSpeech
            ) as T
        }
    }
}
