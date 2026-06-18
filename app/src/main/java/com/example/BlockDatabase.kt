package com.example

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BlockedAppEntity::class, BlockedWebsiteEntity::class], version = 2, exportSchema = false)
abstract class BlockDatabase : RoomDatabase() {
    abstract val dao: BlockedAppDao
    abstract val websiteDao: BlockedWebsiteDao

    companion object {
        @Volatile
        private var INSTANCE: BlockDatabase? = null

        fun getInstance(context: Context): BlockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BlockDatabase::class.java,
                    "blocked_apps_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
