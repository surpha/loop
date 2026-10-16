package com.closetheloop.data

import kotlinx.coroutines.flow.Flow

class LoopRepository(
    private val dao: LoopDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeOpen(): Flow<List<Loop>> = dao.observeOpen()

    suspend fun openWithDeadline(): List<Loop> = dao.getOpenWithDeadline()

    suspend fun get(id: Long): Loop? = dao.getById(id)

    suspend fun add(title: String, deadline: Long?): Long {
        val now = clock()
        return dao.insert(
            Loop(
                title = title,
                createdAt = now,
                deadline = deadline,
                originalDurationMs = deadline?.let { it - now },
            )
        )
    }

    suspend fun close(id: Long) {
        val loop = dao.getById(id) ?: return
        dao.update(loop.copy(status = LoopStatus.CLOSED, closedAt = clock()))
    }

    suspend fun snooze(id: Long, minutes: Int): Loop? {
        val loop = dao.getById(id) ?: return null
        val now = clock()
        val snoozed = loop.copy(
            deadline = now + minutes * 60_000L,
            status = LoopStatus.ACTIVE,
            snoozeCount = loop.snoozeCount + 1,
            lastSnoozedAt = now,
        )
        dao.update(snoozed)
        return snoozed
    }

    suspend fun markExpired(id: Long) {
        val loop = dao.getById(id) ?: return
        if (loop.status == LoopStatus.ACTIVE) dao.update(loop.copy(status = LoopStatus.EXPIRED))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
