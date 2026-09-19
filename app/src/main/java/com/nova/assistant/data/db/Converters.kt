package com.nova.assistant.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCategory(category: MemoryCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): MemoryCategory =
        runCatching { MemoryCategory.valueOf(value) }.getOrDefault(MemoryCategory.FACT)
}
