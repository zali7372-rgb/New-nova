package com.nova.assistant.core.ai

/**
 * Absolute last resort in the provider chain. Guarantees AIProviderManager
 * always has *something* to return even if every other provider throws,
 * times out, or reports unavailable. This is what keeps requirement #4 true:
 * "The application must NEVER completely break because an API is unavailable."
 */
class FallbackAIProvider : AIProvider {

    override val id: String = "fallback"
    override val displayName: String = "Vészhelyzeti válaszadó"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun generate(request: AIRequest): AIResult {
        return AIResult.Success(
            "Jelenleg egyik AI szolgáltatást sem érem el megbízhatóan, ezért nem tudok " +
                "részletes választ adni. Kérlek, próbáld meg egyszerűbben megfogalmazni, vagy " +
                "ellenőrizd az internetkapcsolatot és az AI beállításokat.",
            id
        )
    }
}
