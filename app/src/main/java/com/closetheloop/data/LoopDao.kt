package com.closetheloop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LoopDao {
    /** Non-closed loops: short-term by deadline first, then long-term newest first. */
    @Query(
        """
        SELECT * FROM loops WHERE status != 'CLOSED'
        ORDER BY deadline IS NULL, deadline ASC, createdAt DESC
        """
    )
    fun observeOpen(): Flow<List<Loop>>

    @Query("SELECT * FROM loops WHERE status != 'CLOSED' AND deadline IS NOT NULL ORDER BY deadline ASC")
    suspend fun getOpenWithDeadline(): List<Loop>

    @Query("SELECT * FROM loops WHERE id = :id")
    suspend fun getById(id: Long): Loop?

    @Insert
    suspend fun insert(loop: Loop): Long

    @Update
    suspend fun update(loop: Loop)

    @Query("DELETE FROM loops WHERE id = :id")
    suspend fun delete(id: Long)
}
