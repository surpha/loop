package com.closetheloop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LoopStatus { ACTIVE, EXPIRED, CLOSED }

@Entity(tableName = "loops")
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
) {
    val isShortTerm: Boolean get() = deadline != null

    /** Start of the current countdown window, used for ring progress. */
    val windowStart: Long get() = lastSnoozedAt ?: createdAt
}
