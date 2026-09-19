package com.nova.assistant.system.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,               // not listening, waiting for user to tap mic / say wake word
    LISTENING_FOR_WAKE, // background service is listening only for "Nova"
    LISTENING,          // actively listening to a full command
    THINKING,           // recognized text is being processed by ConversationEngine
    SPEAKING,           // TTS is playing NOVA's reply
    ERROR,              // something failed; UI should show a restart affordance
    UNAVAILABLE         // speech recognition not available on this device
}

/**
 * Wraps Android's [SpeechRecognizer] and [TextToSpeech] behind a small state
 * machine, and implements "continuous conversation mode": after NOVA finishes
 * speaking a reply, it automatically starts listening again for a short
 * window (CONTINUOUS_MODE_WINDOW_MS) WITHOUT requiring the wake word, so a
 * multi-turn exchange doesn't need "Nova" before every sentence. If nothing
 * is said in that window, it falls back to wake-word-only listening.
 *
 * IMPORTANT (spec section 6 / 18): Android aggressively restricts what a
 * background process can do with the microphone once the app is not in the
 * foreground and no foreground service is actively running. This class does
 * NOT pretend true always-on background listening is guaranteed - it exposes
 * [state] truthfully at every step, and NovaListeningService is what keeps
 * listening alive as a real, user-visible foreground service while active.
 * If the OS kills or throttles listening, the state surfaces that instead of
 * silently doing nothing.
 */
class VoiceManager(
    private val context: Context,
    private val onFinalResult: (String) -> Unit
) {
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var continuousModeEnabled = false

    fun currentState(): VoiceState = _state.value

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.UNAVAILABLE
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale("hu", "HU")
                textToSpeech?.setOnUtteranceProgressListener(utteranceProgressListener)
            }
        }

        _state.value = VoiceState.IDLE
    }

    fun setContinuousModeEnabled(enabled: Boolean) {
        continuousModeEnabled = enabled
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            _state.value = VoiceState.UNAVAILABLE
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        _state.value = VoiceState.LISTENING
        runCatching { recognizer.startListening(intent) }
            .onFailure { _state.value = VoiceState.ERROR }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        if (_state.value == VoiceState.LISTENING) _state.value = VoiceState.IDLE
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        _state.value = VoiceState.SPEAKING
        pendingSpeakDoneCallback = onDone
        if (!ttsReady) {
            // TTS unavailable/still initializing - don't hang forever in SPEAKING.
            _state.value = VoiceState.IDLE
            onDone?.invoke()
            return
        }
        val params = Bundle()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "nova_utterance")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        if (_state.value == VoiceState.SPEAKING) _state.value = VoiceState.IDLE
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private var pendingSpeakDoneCallback: (() -> Unit)? = null

    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _state.value = VoiceState.SPEAKING
        }

        override fun onDone(utteranceId: String?) {
            _state.value = VoiceState.IDLE
            pendingSpeakDoneCallback?.invoke()
            pendingSpeakDoneCallback = null
            if (continuousModeEnabled) {
                startListening()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.value = VoiceState.ERROR
            pendingSpeakDoneCallback?.invoke()
            pendingSpeakDoneCallback = null
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            _state.value = VoiceState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) { /* could drive a waveform UI */ }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = VoiceState.THINKING
        }

        override fun onError(error: Int) {
            _state.value = VoiceState.ERROR
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            _state.value = VoiceState.THINKING
            if (!best.isNullOrBlank()) {
                onFinalResult(best)
            } else {
                _state.value = VoiceState.IDLE
            }
        }

        override fun onPartialResults(partialResults: Bundle?) { /* could show live captions */ }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
