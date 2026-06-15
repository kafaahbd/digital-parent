package com.example

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlockedAppRepository(context: Context) {
    private val db = BlockDatabase.getInstance(context)
    private val dao = db.dao

    val allBlockedApps: Flow<List<BlockedAppEntity>> = dao.getAllBlockedApps()

    suspend fun isAppBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext dao.isAppBlocked(packageName)
    }

    suspend fun setAppBlocked(packageName: String, isBlocked: Boolean, dailyTimeLimitMinutes: Int = 0) = withContext(Dispatchers.IO) {
        if (isBlocked) {
            dao.insertOrUpdate(BlockedAppEntity(packageName = packageName, isBlocked = true, dailyTimeLimitMinutes = dailyTimeLimitMinutes))
        } else {
            dao.deleteByPackageName(packageName)
        }
    }

    suspend fun getBlockedApp(packageName: String): BlockedAppEntity? = withContext(Dispatchers.IO) {
        return@withContext dao.getBlockedApp(packageName)
    }
}
