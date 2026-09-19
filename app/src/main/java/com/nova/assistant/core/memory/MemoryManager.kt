package com.nova.assistant.core.memory

import com.nova.assistant.data.db.MemoryCategory
import com.nova.assistant.data.repository.MemoryRepository

/**
 * Core-layer façade over [MemoryRepository]. Skills and CommandProcessor talk
 * to this, not to Room directly - keeps the persistence layer swappable and
 * keeps "what counts as a memory-worthy fact" logic in one place.
 */
class MemoryManager(private val repository: MemoryRepository) {

    suspend fun remember(rawFactText: String): String {
        val cleaned = rawFactText.trim().removePrefix("hogy").trim()
        if (cleaned.isBlank()) {
            return "Nem értettem pontosan, mit jegyezzek meg. Mondd el újra, mit szeretnél, hogy megjegyezzek."
        }
        val category = classify(cleaned)
        repository.save(category, cleaned)
        return "Megjegyeztem: $cleaned"
    }

    suspend fun recall(query: String): String {
        val relevant = repository.searchRelevant(query, limit = 3)
        if (relevant.isEmpty()) {
            val recent = repository.getRecent(3)
            if (recent.isEmpty()) {
                return "Egyelőre nincs semmi elmentve, amire emlékezhetnék rólad."
            }
            return "Nem találtam pontosan idevágót, de nemrég ezeket jegyeztem meg: " +
                recent.joinToString("; ") { it.content }
        }
        return relevant.joinToString("; ") { it.content }
    }

    suspend fun relevantFactsFor(utterance: String): List<String> =
        repository.searchRelevant(utterance, limit = 3).map { it.content }

    suspend fun forgetMostRecent(): String {
        val recent = repository.getRecent(1).firstOrNull()
            ?: return "Nincs mit elfelejtenem, üres a memóriám."
        repository.deleteById(recent.id)
        return "Elfelejtettem: ${recent.content}"
    }

    suspend fun forgetAll(): String {
        repository.deleteAll()
        return "Töröltem az összes elmentett emléket."
    }

    private fun classify(fact: String): MemoryCategory {
        val lower = fact.lowercase()
        return when {
            lower.startsWith("szeretem") || lower.startsWith("nem szeretem") ||
                lower.contains("kedvenc") -> MemoryCategory.PREFERENCE
            else -> MemoryCategory.FACT
        }
    }
}
