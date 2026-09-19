package com.nova.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory {
    PREFERENCE,      // "szeretem az angol zenét"
    FACT,            // "a kutyámat Rexnek hívják"
    CONVERSATION,    // short-lived context worth persisting across sessions
    ALIAS,           // custom app alias, mirrored into AppAliasEntity too
    COMMAND,         // remembered custom command shortcut
    SETTING          // free-form setting note
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: MemoryCategory,
    val content: String,
    val createdAtMillis: Long,
    val lastAccessedAtMillis: Long = createdAtMillis
)
