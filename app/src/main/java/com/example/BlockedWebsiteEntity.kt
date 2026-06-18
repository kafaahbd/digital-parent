package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_websites")
data class BlockedWebsiteEntity(
    @PrimaryKey val urlOrKeyword: String,
    val isKeyword: Boolean = false,
    val isBlocked: Boolean = true
)
