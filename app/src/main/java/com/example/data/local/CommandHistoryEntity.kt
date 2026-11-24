package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single voice or typed command executed by Voice Control AI.
 * Note: Screen content trees are NEVER stored here for user privacy.
 */
@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val commandText: String,
    val language: String,
    val executionMode: String, // "OFFLINE" or "AI_AGENT"
    val status: String, // "SUCCESS", "FAILED", "CANCELLED", "NEEDS_PERMISSION"
    val responseSummary: String,
    val stepCount: Int = 1,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
