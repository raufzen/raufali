package com.example.data.repository

import com.example.data.local.CommandHistoryDao
import com.example.data.local.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow

class CommandHistoryRepository(
    private val dao: CommandHistoryDao
) {
    val allHistory: Flow<List<CommandHistoryEntity>> = dao.observeAll()

    fun recentHistory(limit: Int = 6): Flow<List<CommandHistoryEntity>> = dao.observeRecent(limit)

    fun searchHistory(query: String): Flow<List<CommandHistoryEntity>> =
        if (query.isBlank()) allHistory else dao.search(query.trim())

    suspend fun record(
        commandText: String,
        language: String,
        executionMode: String,
        status: String,
        responseSummary: String,
        stepCount: Int = 1,
        durationMs: Long = 0L
    ): Long {
        return dao.insert(
            CommandHistoryEntity(
                commandText = commandText,
                language = language,
                executionMode = executionMode,
                status = status,
                responseSummary = responseSummary,
                stepCount = stepCount,
                durationMs = durationMs
            )
        )
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
