package com.closetheloop.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class LoopStatus { ACTIVE, EXPIRED, CLOSED }

// Column defaults mirror MIGRATION_1_2 so Room's schema validation matches migrated databases.
@Entity(tableName = "loops", indices = [Index(value = ["syncId"], unique = true)])
data class Loop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    /** Null = long-term to-do (no ring, no alarm). */
    val deadline: Long? = null,
    val originalDurationMs: Long? = null,
    val status: LoopStatus = LoopStatus.ACTIVE,
    val snoozeCount: Int = 0,
    val lastSnoozedAt: Long? = null,
    val closedAt: Long? = null,
    /** Stable id shared across devices. */
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
    /** Last user change, for last-write-wins merging. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    /** Tombstone: deleted locally, kept until the deletion is uploaded. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    /** Changed locally and not yet uploaded. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
) {
    val isShortTerm: Boolean get() = deadline != null

    /** Start of the current countdown window, used for ring progress. */
    val windowStart: Long get() = lastSnoozedAt ?: createdAt
}
