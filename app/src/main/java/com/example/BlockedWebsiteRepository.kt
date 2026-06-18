package com.example

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlockedWebsiteRepository(context: Context) {
    private val db = BlockDatabase.getInstance(context)
    private val dao = db.websiteDao

    val allBlockedWebsites: Flow<List<BlockedWebsiteEntity>> = dao.getAllBlockedWebsites()

    suspend fun addBlockedWebsite(urlOrKeyword: String, isKeyword: Boolean) = withContext(Dispatchers.IO) {
        val entity = BlockedWebsiteEntity(urlOrKeyword = urlOrKeyword.trim().lowercase(), isKeyword = isKeyword, isBlocked = true)
        dao.insertOrUpdate(entity)
    }

    suspend fun removeBlockedWebsite(urlOrKeyword: String) = withContext(Dispatchers.IO) {
        dao.deleteByPattern(urlOrKeyword.trim().lowercase())
    }
}
