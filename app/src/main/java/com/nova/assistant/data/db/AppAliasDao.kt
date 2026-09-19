package com.nova.assistant.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppAliasDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alias: AppAliasEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(aliases: List<AppAliasEntity>)

    @Delete
    suspend fun delete(alias: AppAliasEntity)

    @Query("DELETE FROM app_aliases WHERE packageName = :packageName")
    suspend fun deleteForPackage(packageName: String)

    @Query("SELECT * FROM app_aliases")
    fun observeAll(): Flow<List<AppAliasEntity>>

    @Query("SELECT * FROM app_aliases")
    suspend fun getAll(): List<AppAliasEntity>

    @Query("SELECT COUNT(*) FROM app_aliases")
    suspend fun count(): Int
}
