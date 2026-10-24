package com.closetheloop.data

import com.closetheloop.domain.ExpiryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LoopRepository(
    private val dao: LoopDao,
    private val scheduler: LoopScheduler = LoopScheduler.None,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeOpen(): Flow<List<Loop>> = dao.observeOpen()

    fun observe(id: Long): Flow<Loop?> = dao.observeById(id)

    fun observeClosedSince(since: Long): Flow<Int> = dao.observeClosedSince(since)

    suspend fun openWithDeadline(): List<Loop> = dao.getOpenWithDeadline()

    suspend fun get(id: Long): Loop? = dao.getById(id)

    suspend fun add(title: String, deadline: Long?): Long {
        val now = clock()
        val id = dao.insert(
            Loop(
                title = title,
                createdAt = now,
                deadline = deadline,
                originalDurationMs = deadline?.let { it - now },
            )
        )
        if (deadline != null) scheduler.schedule(id, deadline)
        return id
    }

    suspend fun close(id: Long) {
        val loop = dao.getById(id) ?: return
        val now = clock()
        dao.update(loop.copy(status = LoopStatus.CLOSED, closedAt = now, updatedAt = now, dirty = true))
        scheduler.cancel(id)
    }

    suspend fun snooze(id: Long, minutes: Int): Loop? {
        val loop = dao.getById(id) ?: return null
        val now = clock()
        val snoozed = loop.copy(
            deadline = now + minutes * 60_000L,
            status = LoopStatus.ACTIVE,
            snoozeCount = loop.snoozeCount + 1,
            lastSnoozedAt = now,
            updatedAt = now,
            dirty = true,
        )
        dao.update(snoozed)
        scheduler.cancel(id)
        scheduler.schedule(id, snoozed.deadline!!)
        return snoozed
    }

    /** Undo a close: back to ACTIVE (or EXPIRED if its deadline already passed) and re-armed. */
    suspend fun reopen(id: Long) {
        val loop = dao.getById(id) ?: return
        if (loop.status != LoopStatus.CLOSED) return
        val now = clock()
        val deadline = loop.deadline
        val status = if (deadline != null && deadline <= now) LoopStatus.EXPIRED else LoopStatus.ACTIVE
        val reopened = loop.copy(status = status, closedAt = null, updatedAt = now, dirty = true)
        dao.update(reopened)
        ExpiryPolicy.rearmAt(reopened, now)?.let { scheduler.schedule(id, it) }
    }

    /** Alarm-driven and derivable on every device, so it stays local: no updatedAt bump, not dirty. */
    suspend fun markExpired(id: Long) {
        val loop = dao.getById(id) ?: return
        if (loop.status == LoopStatus.ACTIVE) dao.update(loop.copy(status = LoopStatus.EXPIRED))
    }

    /** Renames a loop; a non-null [deadline] different from the current one also restarts its countdown. */
    suspend fun edit(id: Long, title: String, deadline: Long?): Loop? {
        val loop = dao.getById(id) ?: return null
        val now = clock()
        if (deadline == null || deadline == loop.deadline) {
            return loop.copy(title = title, updatedAt = now, dirty = true).also { dao.update(it) }
        }
        val edited = loop.copy(
            title = title,
            deadline = deadline,
            originalDurationMs = deadline - now,
            status = LoopStatus.ACTIVE,
            // Window restarts at the edit, so the ring starts full again.
            lastSnoozedAt = now,
            updatedAt = now,
            dirty = true,
        )
        dao.update(edited)
        scheduler.cancel(id)
        scheduler.schedule(id, deadline)
        return edited
    }

    /** Soft delete: the tombstone syncs the deletion to other devices, then is purged. */
    suspend fun delete(id: Long) {
        val loop = dao.getById(id) ?: return
        dao.update(loop.copy(deleted = true, updatedAt = clock(), dirty = true))
        scheduler.cancel(id)
    }

    // --- Sync ---

    suspend fun pendingUploads(): List<RemoteLoop> = dao.getDirty().map { it.toRemote() }

    fun observePendingUploads(): Flow<List<RemoteLoop>> = dao.observeDirty().map { rows -> rows.map { it.toRemote() } }

    /** Called after [remote] reached the server; a newer local edit since then stays dirty. */
    suspend fun markUploaded(remote: RemoteLoop) {
        dao.markClean(remote.syncId, remote.updatedAt)
        if (remote.deleted) dao.purgeUploadedTombstone(remote.syncId)
    }

    /** Merges a change from another device (last write wins on [RemoteLoop.updatedAt]) and fixes up alarms. */
    suspend fun applyRemote(remote: RemoteLoop) {
        val local = dao.getBySyncId(remote.syncId)
        if (local != null && local.updatedAt >= remote.updatedAt) return
        if (remote.deleted) {
            if (local != null) {
                dao.delete(local.id)
                scheduler.cancel(local.id)
            }
            return
        }
        var merged = remote.toLocal(local?.id ?: 0)
        // EXPIRED is set locally by the alarm; don't regress it for an unrelated change (e.g. a rename).
        if (local?.status == LoopStatus.EXPIRED && merged.status == LoopStatus.ACTIVE && merged.deadline == local.deadline) {
            merged = merged.copy(status = LoopStatus.EXPIRED)
        }
        val id = if (local == null) dao.insert(merged) else merged.id.also { dao.update(merged) }

        val timingChanged = local == null || local.deadline != merged.deadline || local.status != merged.status
        if (!timingChanged) return
        // cancel also dismisses a Time's-up alert, e.g. when the loop was closed or snoozed on the other device.
        scheduler.cancel(id)
        ExpiryPolicy.rearmAt(merged, clock())?.let { scheduler.schedule(id, it) }
    }
}
