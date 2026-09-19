package com.nova.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MemoryEntity::class, AppAliasEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NovaDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun appAliasDao(): AppAliasDao

    companion object {
        @Volatile
        private var instance: NovaDatabase? = null

        fun getInstance(context: Context): NovaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaDatabase::class.java,
                    "nova_memory.db"
                ).build().also { instance = it }
            }
        }
    }
}
