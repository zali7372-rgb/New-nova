package com.nova.assistant.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.nova.assistant.R
import com.nova.assistant.ui.aliases.AliasManagerActivity
import com.nova.assistant.ui.memory.MemoryActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .commit()
        title = getString(R.string.settings_title)
    }

    class SettingsFragment : androidx.preference.PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<androidx.preference.Preference>("open_memory")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), MemoryActivity::class.java))
                true
            }
            findPreference<androidx.preference.Preference>("open_aliases")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AliasManagerActivity::class.java))
                true
            }
            findPreference<androidx.preference.Preference>("open_laptop_pairing")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), com.nova.assistant.ui.laptop.LaptopPairingActivity::class.java))
                true
            }

            val app = requireActivity().application as com.nova.assistant.NovaApplication
            findPreference<androidx.preference.ListPreference>("ai_provider")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    app.settingsRepository.setPreferredAiProviderId(newValue as String)
                    true
                }
            findPreference<androidx.preference.EditTextPreference>("api_key_override")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    app.settingsRepository.setRemoteApiKeyOverride((newValue as? String).orEmpty())
                    true
                }
            findPreference<androidx.preference.SwitchPreferenceCompat>("voice_enabled")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    app.settingsRepository.voiceEnabled = newValue as Boolean
                    true
                }
            findPreference<androidx.preference.SwitchPreferenceCompat>("continuous_listening_enabled")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    app.settingsRepository.continuousListeningEnabled = newValue as Boolean
                    app.voiceManager.setContinuousModeEnabled(newValue)
                    true
                }
            findPreference<androidx.preference.SwitchPreferenceCompat>("tts_enabled")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    app.settingsRepository.ttsEnabled = newValue as Boolean
                    true
                }
            findPreference<androidx.preference.SwitchPreferenceCompat>("wake_word_enabled")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    app.settingsRepository.wakeWordEnabled = newValue as Boolean
                    true
                }
        }
    }
}
