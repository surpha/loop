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
        SELECT * FROM loops WHERE status != 'CLOSED' AND deleted = 0
        ORDER BY deadline IS NULL, deadline ASC, createdAt DESC
        """
    )
    fun observeOpen(): Flow<List<Loop>>

    @Query("SELECT * FROM loops WHERE status != 'CLOSED' AND deleted = 0 AND deadline IS NOT NULL ORDER BY deadline ASC")
    suspend fun getOpenWithDeadline(): List<Loop>

    @Query("SELECT * FROM loops WHERE id = :id AND deleted = 0")
    suspend fun getById(id: Long): Loop?

    @Query("SELECT * FROM loops WHERE id = :id AND deleted = 0")
    fun observeById(id: Long): Flow<Loop?>

    @Query("SELECT COUNT(*) FROM loops WHERE status = 'CLOSED' AND deleted = 0 AND closedAt >= :since")
    fun observeClosedSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(loop: Loop): Long

    @Update
    suspend fun update(loop: Loop)

    @Query("DELETE FROM loops WHERE id = :id")
    suspend fun delete(id: Long)

    // --- Sync ---

    /** Includes tombstones. */
    @Query("SELECT * FROM loops WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): Loop?

    /** Local changes waiting to upload, including tombstones. */
    @Query("SELECT * FROM loops WHERE dirty = 1")
    suspend fun getDirty(): List<Loop>

    @Query("SELECT * FROM loops WHERE dirty = 1")
    fun observeDirty(): Flow<List<Loop>>

    /** Clears the dirty flag only if the row wasn't changed again since [updatedAt] was uploaded. */
    @Query("UPDATE loops SET dirty = 0 WHERE syncId = :syncId AND updatedAt = :updatedAt")
    suspend fun markClean(syncId: String, updatedAt: Long)

    @Query("DELETE FROM loops WHERE syncId = :syncId AND deleted = 1 AND dirty = 0")
    suspend fun purgeUploadedTombstone(syncId: String)
}
