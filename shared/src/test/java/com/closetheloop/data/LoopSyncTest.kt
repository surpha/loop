package com.closetheloop.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoopSyncTest {
    private lateinit var db: LoopDatabase
    private lateinit var repo: LoopRepository
    private var now = 1_000_000L
    private val scheduled = mutableMapOf<Long, Long>()
    private val cancelled = mutableListOf<Long>()
    private val scheduler = object : LoopScheduler {
        override fun schedule(loopId: Long, at: Long) { scheduled[loopId] = at }
        override fun cancel(loopId: Long) { scheduled.remove(loopId); cancelled += loopId }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LoopRepository(db.loopDao(), scheduler) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun remote(
        syncId: String = "s1",
        title: String = "Remote",
        deadline: Long? = now + 60_000,
        status: LoopStatus = LoopStatus.ACTIVE,
        updatedAt: Long = now,
        deleted: Boolean = false,
    ) = RemoteLoop(syncId, title, createdAt = now - 1_000, deadline, null, status, 0, null, null, updatedAt, deleted)

    @Test
    fun localChangesAreDirtyUntilUploaded() = runTest {
        val id = repo.add("task", now + 60_000)
        val pending = repo.pendingUploads().single()
        assertEquals("task", pending.title)

        repo.markUploaded(pending)
        assertTrue(repo.pendingUploads().isEmpty())

        now += 10
        repo.close(id)
        assertEquals(LoopStatus.CLOSED, repo.pendingUploads().single().status)
    }

    @Test
    fun editDuringUploadStaysDirty() = runTest {
        val id = repo.add("v1", null)
        val uploading = repo.pendingUploads().single()
        now += 10
        repo.edit(id, "v2", null)
        repo.markUploaded(uploading)
        assertEquals("v2", repo.pendingUploads().single().title)
    }

    @Test
    fun deleteIsATombstoneUntilUploaded() = runTest {
        val id = repo.add("task", now + 60_000)
        repo.markUploaded(repo.pendingUploads().single())
        repo.delete(id)

        assertEquals(emptyList<Loop>(), repo.observeOpen().first())
        val tombstone = repo.pendingUploads().single()
        assertTrue(tombstone.deleted)

        repo.markUploaded(tombstone)
        assertNull(db.loopDao().getBySyncId(tombstone.syncId))
    }

    @Test
    fun remoteInsertArmsAlarm() = runTest {
        repo.applyRemote(remote())
        val loop = repo.observeOpen().first().single()
        assertEquals("Remote", loop.title)
        assertFalse(loop.dirty)
        assertEquals(mapOf(loop.id to now + 60_000), scheduled)
    }

    @Test
    fun olderRemoteLosesToNewerLocal() = runTest {
        repo.applyRemote(remote(updatedAt = now))
        val id = repo.observeOpen().first().single().id
        now += 1_000
        repo.edit(id, "Local rename", null)

        repo.applyRemote(remote(title = "Stale", updatedAt = now - 500))
        assertEquals("Local rename", repo.get(id)!!.title)
    }

    @Test
    fun remoteCloseCancelsAlarmAndAlert() = runTest {
        repo.applyRemote(remote())
        val id = repo.observeOpen().first().single().id
        repo.applyRemote(remote(status = LoopStatus.CLOSED, updatedAt = now + 1))

        assertTrue(repo.observeOpen().first().isEmpty())
        assertTrue(id in cancelled)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun remoteDeleteRemovesRow() = runTest {
        repo.applyRemote(remote())
        repo.applyRemote(remote(deleted = true, updatedAt = now + 1))
        assertTrue(repo.observeOpen().first().isEmpty())
        assertNull(db.loopDao().getBySyncId("s1"))
    }

    @Test
    fun remoteRenameKeepsLocalExpiredAndAlarm() = runTest {
        repo.applyRemote(remote(deadline = now + 1_000))
        val id = repo.observeOpen().first().single().id
        now += 2_000
        repo.markExpired(id)
        scheduled[id] = now + 180_000
        cancelled.clear()

        repo.applyRemote(remote(title = "Renamed", deadline = now - 1_000, updatedAt = now))
        val loop = repo.get(id)!!
        assertEquals("Renamed", loop.title)
        assertEquals(LoopStatus.EXPIRED, loop.status)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun markExpiredStaysLocal() = runTest {
        val id = repo.add("task", now + 1_000)
        repo.markUploaded(repo.pendingUploads().single())
        repo.markExpired(id)
        assertTrue(repo.pendingUploads().isEmpty())
    }
}
