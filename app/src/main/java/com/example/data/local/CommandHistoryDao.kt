package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CommandHistoryEntity>>

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 6): Flow<List<CommandHistoryEntity>>

    @Query(
        """
        SELECT * FROM command_history
        WHERE commandText LIKE '%' || :query || '%'
           OR responseSummary LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        """
    )
    fun search(query: String): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
