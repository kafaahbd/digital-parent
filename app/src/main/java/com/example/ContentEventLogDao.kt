package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentEventLogDao {
    @Query("SELECT * FROM content_event_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ContentEventLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ContentEventLogEntity)

    @Query("DELETE FROM content_event_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM content_event_logs WHERE id = :logId")
    suspend fun deleteLogById(logId: Long)
}
