package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getBlockedApp(packageName: String): BlockedAppEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName AND isBlocked = 1 LIMIT 1)")
    suspend fun isAppBlocked(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: BlockedAppEntity)

    @Delete
    suspend fun delete(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
