package com.nova.assistant.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT * FROM memories ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY createdAtMillis DESC")
    suspend fun getByCategory(category: MemoryCategory): List<MemoryEntity>

    /**
     * Very simple relevance search: substring match against each significant
     * word in the query. Good enough for a local, on-device memory store
     * without pulling in a full-text search / embeddings dependency.
     */
    @Query("SELECT * FROM memories WHERE content LIKE '%' || :keyword || '%' ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun searchByKeyword(keyword: String, limit: Int = 5): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<MemoryEntity>

    @Query("UPDATE memories SET lastAccessedAtMillis = :accessedAt WHERE id = :id")
    suspend fun touch(id: Long, accessedAt: Long)
}
