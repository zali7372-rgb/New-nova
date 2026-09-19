package com.nova.assistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Backing store for everything in the Settings screen. Two SharedPreferences
 * are used deliberately:
 *  - [prefs]: ordinary, unencrypted settings (toggles, language, theme) -
 *    nothing sensitive.
 *  - [securePrefs]: EncryptedSharedPreferences, used ONLY for an
 *    optionally-user-entered remote AI API key override. This is how a user
 *    can supply their own key at runtime without it ever being written to
 *    source code or a git-tracked file (spec #4 / #21: never hardcode or leak
 *    secrets).
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nova_secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Voice ---
    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    var continuousListeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS_LISTENING, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS_LISTENING, value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var speechLanguageTag: String
        get() = prefs.getString(KEY_SPEECH_LANGUAGE, "hu-HU") ?: "hu-HU"
        set(value) = prefs.edit().putString(KEY_SPEECH_LANGUAGE, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    // --- AI provider ---
    fun getPreferredAiProviderId(): String = prefs.getString(KEY_AI_PROVIDER, "local") ?: "local"

    fun setPreferredAiProviderId(id: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, id).apply()
    }

    fun getRemoteApiKeyOverride(): String = securePrefs.getString(KEY_API_KEY_OVERRIDE, "") ?: ""

    fun setRemoteApiKeyOverride(key: String) {
        securePrefs.edit().putString(KEY_API_KEY_OVERRIDE, key).apply()
    }

    fun clearRemoteApiKeyOverride() {
        securePrefs.edit().remove(KEY_API_KEY_OVERRIDE).apply()
    }

    // --- Theme ---
    var darkThemeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    // --- Laptop pairing (see system.bridge.LaptopBridgeManager) ---
    var laptopIp: String
        get() = prefs.getString(KEY_LAPTOP_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAPTOP_IP, value).apply()

    var laptopPort: Int
        get() = prefs.getInt(KEY_LAPTOP_PORT, 8765)
        set(value) = prefs.edit().putInt(KEY_LAPTOP_PORT, value).apply()

    var laptopPin: String
        get() = prefs.getString(KEY_LAPTOP_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAPTOP_PIN, value).apply()

    /** Whether voice/text input should route to the laptop instead of being
     *  answered by this phone's own ConversationEngine. Independent of
     *  whether a connection currently happens to be live. */
    var laptopModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_LAPTOP_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LAPTOP_MODE_ENABLED, value).apply()

    /** false (default) = send recognized text (robust, works with the
     *  laptop offline for STT). true = stream raw microphone audio to the
     *  laptop for it to transcribe (experimental, needs laptop internet). */
    var laptopMicStreamingEnabled: Boolean
        get() = prefs.getBoolean(KEY_LAPTOP_MIC_STREAMING, false)
        set(value) = prefs.edit().putBoolean(KEY_LAPTOP_MIC_STREAMING, value).apply()

    companion object {
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_CONTINUOUS_LISTENING = "continuous_listening_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_SPEECH_LANGUAGE = "speech_language"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_API_KEY_OVERRIDE = "api_key_override"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
        private const val KEY_LAPTOP_IP = "laptop_ip"
        private const val KEY_LAPTOP_PORT = "laptop_port"
        private const val KEY_LAPTOP_PIN = "laptop_pin"
        private const val KEY_LAPTOP_MODE_ENABLED = "laptop_mode_enabled"
        private const val KEY_LAPTOP_MIC_STREAMING = "laptop_mic_streaming_enabled"
    }
}
