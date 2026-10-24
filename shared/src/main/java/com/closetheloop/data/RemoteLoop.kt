package com.closetheloop.data

/** The synced shape of a loop, shared by all devices (local [Loop.id] is device-specific and never synced). */
data class RemoteLoop(
    val syncId: String,
    val title: String,
    val createdAt: Long,
    val deadline: Long?,
    val originalDurationMs: Long?,
    val status: LoopStatus,
    val snoozeCount: Int,
    val lastSnoozedAt: Long?,
    val closedAt: Long?,
    val updatedAt: Long,
    val deleted: Boolean,
)

fun Loop.toRemote() = RemoteLoop(
    syncId = syncId,
    title = title,
    createdAt = createdAt,
    deadline = deadline,
    originalDurationMs = originalDurationMs,
    status = status,
    snoozeCount = snoozeCount,
    lastSnoozedAt = lastSnoozedAt,
    closedAt = closedAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

/** Applies remote fields onto an existing local row (keeping its local id) or a fresh one. */
fun RemoteLoop.toLocal(localId: Long = 0) = Loop(
    id = localId,
    syncId = syncId,
    title = title,
    createdAt = createdAt,
    deadline = deadline,
    originalDurationMs = originalDurationMs,
    status = status,
    snoozeCount = snoozeCount,
    lastSnoozedAt = lastSnoozedAt,
    closedAt = closedAt,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = false,
)
