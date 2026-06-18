package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_event_logs")
data class ContentEventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val appName: String,
    val packageName: String,
    val detectedText: String,
    val category: String,
    val severity: String,
    val actionTaken: String
)
