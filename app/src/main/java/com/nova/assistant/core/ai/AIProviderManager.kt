package com.nova.assistant.core.ai

/**
 * Orchestrates the provider chain: preferred provider first (whatever the
 * user picked in Settings), then Local, then Fallback. This is the ONE place
 * in NOVA that knows about provider ordering - everything else just asks
 * AIProviderManager for an answer and gets one, guaranteed.
 */
class AIProviderManager(
    private val remoteProvider: AIProvider?,
    private val localProvider: AIProvider,
    private val fallbackProvider: AIProvider,
    private var preferredProviderId: String = localProvider.id
) {

    fun setPreferredProvider(providerId: String) {
        preferredProviderId = providerId
    }

    fun availableProviders(): List<AIProvider> =
        listOfNotNull(remoteProvider, localProvider, fallbackProvider)

    suspend fun answer(request: AIRequest): AIResult {
        val orderedChain = buildChain()
        var lastFailure: AIResult.Failure? = null

        for (provider in orderedChain) {
            val available = runCatching { provider.isAvailable() }.getOrDefault(false)
            if (!available) continue

            val result = runCatching { provider.generate(request) }
                .getOrElse { AIResult.Failure(it.message ?: "Ismeretlen hiba", provider.id) }

            when (result) {
                is AIResult.Success -> return result
                is AIResult.Failure -> lastFailure = result
            }
        }

        // Every provider either was unavailable or failed. Fallback ALWAYS
        // returns Success, so this line is reachable only in pathological
        // test doubles - but we still handle it defensively.
        return fallbackProvider.generate(request).let {
            if (it is AIResult.Success) it
            else AIResult.Success(
                lastFailure?.reason ?: "Nem sikerült választ generálni.",
                "fallback"
            )
        }
    }

    private fun buildChain(): List<AIProvider> {
        val preferred = availableProviders().firstOrNull { it.id == preferredProviderId }
        val rest = availableProviders().filter { it.id != preferredProviderId }
        val chain = mutableListOf<AIProvider>()
        preferred?.let { chain.add(it) }
        chain.addAll(rest)
        if (chain.none { it.id == fallbackProvider.id }) chain.add(fallbackProvider)
        return chain
    }
}
