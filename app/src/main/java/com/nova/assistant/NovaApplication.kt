package com.nova.assistant

import android.app.Application
import com.nova.assistant.core.ConversationEngine
import com.nova.assistant.core.ai.AIProviderManager
import com.nova.assistant.core.ai.FallbackAIProvider
import com.nova.assistant.core.ai.LocalAIProvider
import com.nova.assistant.core.ai.RemoteAIProvider
import com.nova.assistant.core.command.CommandProcessor
import com.nova.assistant.core.context.ContextManager
import com.nova.assistant.core.intent.IntentEngine
import com.nova.assistant.core.memory.MemoryManager
import com.nova.assistant.core.personality.PersonalityManager
import com.nova.assistant.core.planner.Planner
import com.nova.assistant.core.response.ResponseGenerator
import com.nova.assistant.core.skills.AppSkill
import com.nova.assistant.core.skills.BrowserSkill
import com.nova.assistant.core.skills.ConversationSkill
import com.nova.assistant.core.skills.DeviceSkill
import com.nova.assistant.core.skills.MemorySkill
import com.nova.assistant.core.skills.SearchSkill
import com.nova.assistant.core.skills.SettingsSkill
import com.nova.assistant.core.skills.SkillManager
import com.nova.assistant.core.skills.TimeSkill
import com.nova.assistant.core.skills.VoiceSkill
import com.nova.assistant.core.skills.WeatherSkill
import com.nova.assistant.data.db.NovaDatabase
import com.nova.assistant.data.repository.MemoryRepository
import com.nova.assistant.data.repository.SettingsRepository
import com.nova.assistant.system.apps.AppManager
import com.nova.assistant.system.bridge.AudioStreamer
import com.nova.assistant.system.bridge.LaptopBridgeManager
import com.nova.assistant.system.device.DeviceActionManager
import com.nova.assistant.system.permissions.PermissionManager
import com.nova.assistant.system.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * NOVA intentionally uses a small hand-written composition root instead of a
 * DI framework (spec #14: "avoid unnecessary libraries"). Every core
 * component is a plain constructor-injected class, which keeps the whole
 * pipeline unit-testable without any framework at all (see app/src/test).
 *
 * A request to open Settings, triggered from voice ("Nova, nyisd meg a Nova
 * beállításait"), is delivered to the UI layer via [settingsRequests] rather
 * than SettingsSkill holding an Activity/Context reference directly.
 */
class NovaApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits raw recognized speech text. MainViewModel is the single consumer
     *  that actually calls ConversationEngine, so every turn (voice or typed)
     *  goes through the same code path and shows up in the chat transcript. */
    val recognizedSpeech = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    lateinit var database: NovaDatabase
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var permissionManager: PermissionManager
        private set
    lateinit var appManager: AppManager
        private set
    lateinit var memoryManager: MemoryManager
        private set
    lateinit var personalityManager: PersonalityManager
        private set
    lateinit var voiceManager: VoiceManager
        private set
    lateinit var conversationEngine: ConversationEngine
        private set
    lateinit var skillManager: SkillManager
        private set
    lateinit var laptopBridgeManager: LaptopBridgeManager
        private set
    lateinit var audioStreamer: AudioStreamer
        private set

    override fun onCreate() {
        super.onCreate()

        database = NovaDatabase.getInstance(this)
        memoryRepository = MemoryRepository(database.memoryDao(), database.appAliasDao())
        settingsRepository = SettingsRepository(this)
        permissionManager = PermissionManager(this)

        appManager = AppManager(this, memoryRepository)
        memoryManager = MemoryManager(memoryRepository)
        personalityManager = PersonalityManager()

        val deviceActionManager = DeviceActionManager(this)
        val contextManager = ContextManager()

        val remoteProvider = buildRemoteProvider()
        val aiProviderManager = AIProviderManager(
            remoteProvider = remoteProvider,
            localProvider = LocalAIProvider(),
            fallbackProvider = FallbackAIProvider(),
            preferredProviderId = settingsRepository.getPreferredAiProviderId()
        )

        voiceManager = VoiceManager(this) { recognizedText ->
            recognizedSpeech.tryEmit(recognizedText)
        }

        val skills = listOf(
            AppSkill(appManager),
            BrowserSkill(this),
            SearchSkill(this),
            MemorySkill(memoryManager),
            DeviceSkill(deviceActionManager),
            TimeSkill(),
            WeatherSkill(),
            SettingsSkill { settingsRequests.tryEmit(Unit) },
            VoiceSkill(voiceManager),
            ConversationSkill(aiProviderManager, contextManager, memoryManager, personalityManager)
        )
        skillManager = SkillManager(skills)

        conversationEngine = ConversationEngine(
            planner = Planner(IntentEngine()),
            commandProcessor = CommandProcessor(skillManager, contextManager, ResponseGenerator()),
            contextManager = contextManager
        )

        applicationScope.launch {
            appManager.ensureDefaultAliasesSeeded()
        }

        laptopBridgeManager = LaptopBridgeManager()
        audioStreamer = AudioStreamer(this, laptopBridgeManager)
    }

    private fun buildRemoteProvider(): RemoteAIProvider? {
        val endpoint = BuildConfig.NOVA_REMOTE_AI_ENDPOINT
        val key = settingsRepository.getRemoteApiKeyOverride().ifBlank { BuildConfig.NOVA_REMOTE_AI_API_KEY }
        if (endpoint.isBlank()) return null
        return RemoteAIProvider(endpoint = endpoint, apiKey = key)
    }
}
