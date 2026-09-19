package com.nova.assistant

import com.google.common.truth.Truth.assertThat
import com.nova.assistant.core.command.CommandProcessor
import com.nova.assistant.core.context.ContextManager
import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import com.nova.assistant.core.response.ResponseGenerator
import com.nova.assistant.core.skills.Skill
import com.nova.assistant.core.skills.SkillManager
import com.nova.assistant.core.skills.SkillResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeSkill(
    override val name: String,
    override val supportedIntents: Set<NovaIntent>,
    private val reply: String
) : Skill {
    override val description = "fake"
    override val requiredPermissions: Set<String> = emptySet()
    var callCount = 0
        private set

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        callCount++
        return SkillResult(reply)
    }
}

class SkillManagerAndCommandProcessorTest {

    @Test
    fun `SkillManager routes to the skill that declares the intent`() = runTest {
        val appSkill = FakeSkill("AppSkillFake", setOf(NovaIntent.OPEN_APP), "megnyitva")
        val timeSkill = FakeSkill("TimeSkillFake", setOf(NovaIntent.TIME), "12:00 van")
        val manager = SkillManager(listOf(appSkill, timeSkill))

        val result = manager.handle(
            IntentResult(NovaIntent.OPEN_APP, 0.9f, originalUtterance = "nyisd meg a chrome-ot")
        )

        assertThat(result.spokenResponse).isEqualTo("megnyitva")
        assertThat(appSkill.callCount).isEqualTo(1)
        assertThat(timeSkill.callCount).isEqualTo(0)
    }

    @Test
    fun `SkillManager returns graceful message for unhandled intent`() = runTest {
        val manager = SkillManager(emptyList())

        val result = manager.handle(
            IntentResult(NovaIntent.WEATHER, 0.9f, originalUtterance = "milyen ido van")
        )

        assertThat(result.spokenResponse).isNotEmpty()
    }

    @Test
    fun `CommandProcessor executes multi-step plan in order and combines responses`() = runTest {
        val appSkill = FakeSkill("AppSkillFake", setOf(NovaIntent.OPEN_APP), "Megnyitottam a Chrome-ot.")
        val searchSkill = FakeSkill("SearchSkillFake", setOf(NovaIntent.SEARCH_WEB), "Kerestem rá.")
        val manager = SkillManager(listOf(appSkill, searchSkill))
        val contextManager = ContextManager()
        val processor = CommandProcessor(manager, contextManager, ResponseGenerator())

        val steps = listOf(
            IntentResult(NovaIntent.OPEN_APP, 0.9f, originalUtterance = "nyisd meg a chrome-ot"),
            IntentResult(NovaIntent.SEARCH_WEB, 0.9f, originalUtterance = "keress ra valamire")
        )

        val result = processor.process(steps)

        assertThat(result.spokenResponse).contains("Chrome")
        assertThat(result.spokenResponse).contains("Kerestem")
    }

    @Test
    fun `CommandProcessor resolves CONVERSATION follow-up against last topic`() = runTest {
        val weatherSkill = FakeSkill("WeatherSkillFake", setOf(NovaIntent.WEATHER), "Meleg lesz.")
        val manager = SkillManager(listOf(weatherSkill))
        val contextManager = ContextManager()
        contextManager.setTopic(NovaIntent.WEATHER, mapOf("location" to "Budapest"))
        val processor = CommandProcessor(manager, contextManager, ResponseGenerator())

        val followUp = IntentResult(
            NovaIntent.CONVERSATION,
            0.6f,
            slots = mapOf("location" to "Szeged"),
            originalUtterance = "és szegeden?"
        )

        val result = processor.process(listOf(followUp))

        assertThat(result.spokenResponse).isEqualTo("Meleg lesz.")
        assertThat(weatherSkill.callCount).isEqualTo(1)
    }
}
