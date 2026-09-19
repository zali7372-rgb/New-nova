package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import com.nova.assistant.system.voice.VoiceManager

/**
 * Not tied to a specific NovaIntent trigger phrase list of its own - voice
 * on/off is primarily a Settings toggle (see SettingsActivity) - but exposed
 * as a Skill too so CommandProcessor / future intents ("Nova, hallgass el")
 * can control the voice subsystem through the same uniform Skill interface
 * as everything else.
 */
class VoiceSkill(private val voiceManager: VoiceManager) : Skill {
    override val name = "VoiceSkill"
    override val description = "Hangalapú interakció vezérlése (beszédfelismerés, TTS)."
    override val supportedIntents: Set<NovaIntent> = emptySet()
    override val requiredPermissions: Set<String> = setOf(android.Manifest.permission.RECORD_AUDIO)

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        return SkillResult("A hangrendszer állapota: ${voiceManager.currentState().name}")
    }

    fun stopSpeaking() = voiceManager.stopSpeaking()
}
