package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedWebsiteDao {
    @Query("SELECT * FROM blocked_websites")
    fun getAllBlockedWebsites(): Flow<List<BlockedWebsiteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(website: BlockedWebsiteEntity)

    @Delete
    suspend fun delete(website: BlockedWebsiteEntity)

    @Query("DELETE FROM blocked_websites WHERE urlOrKeyword = :urlOrKeyword")
    suspend fun deleteByPattern(urlOrKeyword: String)
}
