package com.nova.assistant

import com.google.common.truth.Truth.assertThat
import com.nova.assistant.core.ai.AIProvider
import com.nova.assistant.core.ai.AIProviderManager
import com.nova.assistant.core.ai.AIRequest
import com.nova.assistant.core.ai.AIResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeProvider(
    override val id: String,
    private val available: Boolean,
    private val shouldThrow: Boolean = false,
    private val response: String = "ok-$id"
) : AIProvider {
    override val displayName: String = id
    override suspend fun isAvailable(): Boolean = available
    override suspend fun generate(request: AIRequest): AIResult {
        if (shouldThrow) throw RuntimeException("boom")
        return AIResult.Success(response, id)
    }
}

class AIProviderManagerTest {

    private val dummyRequest = AIRequest(utterance = "teszt", recentTurns = emptyList())

    @Test
    fun `uses preferred provider when available`() = runTest {
        val remote = FakeProvider("remote", available = true)
        val local = FakeProvider("local", available = true)
        val fallback = FakeProvider("fallback", available = true)
        val manager = AIProviderManager(remote, local, fallback, preferredProviderId = "remote")

        val result = manager.answer(dummyRequest)

        assertThat(result).isInstanceOf(AIResult.Success::class.java)
        assertThat((result as AIResult.Success).providerId).isEqualTo("remote")
    }

    @Test
    fun `falls back to local when remote unavailable`() = runTest {
        val remote = FakeProvider("remote", available = false)
        val local = FakeProvider("local", available = true)
        val fallback = FakeProvider("fallback", available = true)
        val manager = AIProviderManager(remote, local, fallback, preferredProviderId = "remote")

        val result = manager.answer(dummyRequest) as AIResult.Success

        assertThat(result.providerId).isEqualTo("local")
    }

    @Test
    fun `falls back to fallback provider when everything else throws`() = runTest {
        val remote = FakeProvider("remote", available = true, shouldThrow = true)
        val local = FakeProvider("local", available = true, shouldThrow = true)
        val fallback = FakeProvider("fallback", available = true)
        val manager = AIProviderManager(remote, local, fallback, preferredProviderId = "remote")

        val result = manager.answer(dummyRequest) as AIResult.Success

        assertThat(result.providerId).isEqualTo("fallback")
    }

    @Test
    fun `never throws even if all providers report unavailable`() = runTest {
        val remote = FakeProvider("remote", available = false)
        val local = FakeProvider("local", available = false)
        val fallback = FakeProvider("fallback", available = true)
        val manager = AIProviderManager(remote, local, fallback, preferredProviderId = "remote")

        val result = manager.answer(dummyRequest)

        assertThat(result).isInstanceOf(AIResult.Success::class.java)
    }
}
