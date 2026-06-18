package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ContentEventLogRepository(context: Context) {
    private val db = BlockDatabase.getInstance(context)
    private val dao = db.contentEventLogDao

    val allLogs: Flow<List<ContentEventLogEntity>> = dao.getAllLogs()

    suspend fun logEvent(
        appName: String,
        packageName: String,
        detectedText: String,
        category: String,
        severity: String,
        actionTaken: String
    ) = withContext(Dispatchers.IO) {
        val entity = ContentEventLogEntity(
            timestamp = System.currentTimeMillis(),
            appName = appName,
            packageName = packageName,
            detectedText = detectedText,
            category = category,
            severity = severity,
            actionTaken = actionTaken
        )
        dao.insertLog(entity)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        dao.clearAllLogs()
    }

    suspend fun deleteLog(logId: Long) = withContext(Dispatchers.IO) {
        dao.deleteLogById(logId)
    }
}
