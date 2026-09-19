package com.nova.assistant.data.repository

import com.nova.assistant.data.db.AppAliasDao
import com.nova.assistant.data.db.AppAliasEntity
import com.nova.assistant.data.db.MemoryCategory
import com.nova.assistant.data.db.MemoryDao
import com.nova.assistant.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val appAliasDao: AppAliasDao
) {
    fun observeMemories(): Flow<List<MemoryEntity>> = memoryDao.observeAll()

    suspend fun save(category: MemoryCategory, content: String): Long {
        return memoryDao.insert(
            MemoryEntity(
                category = category,
                content = content,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(memory: MemoryEntity) = memoryDao.delete(memory)

    suspend fun deleteById(id: Long) = memoryDao.deleteById(id)

    suspend fun deleteAll() = memoryDao.deleteAll()

    suspend fun getByCategory(category: MemoryCategory) = memoryDao.getByCategory(category)

    suspend fun getRecent(limit: Int = 10) = memoryDao.getRecent(limit)

    /** Splits [query] into significant words (3+ chars) and searches each, deduping results. */
    suspend fun searchRelevant(query: String, limit: Int = 5): List<MemoryEntity> {
        val words = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()

        if (words.isEmpty()) return emptyList()

        val results = LinkedHashMap<Long, MemoryEntity>()
        for (word in words) {
            memoryDao.searchByKeyword(word, limit).forEach { results[it.id] = it }
            if (results.size >= limit) break
        }
        return results.values.take(limit)
    }

    fun observeAliases(): Flow<List<AppAliasEntity>> = appAliasDao.observeAll()

    suspend fun getAllAliases(): List<AppAliasEntity> = appAliasDao.getAll()

    suspend fun addAlias(packageName: String, alias: String) =
        appAliasDao.insert(AppAliasEntity(packageName, alias.lowercase().trim()))

    suspend fun seedDefaultAliasesIfEmpty(defaults: List<AppAliasEntity>) {
        if (appAliasDao.count() == 0) {
            appAliasDao.insertAll(defaults)
        }
    }

    suspend fun removeAlias(alias: AppAliasEntity) = appAliasDao.delete(alias)
}
