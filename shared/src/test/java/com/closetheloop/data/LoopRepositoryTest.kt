package com.closetheloop.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoopRepositoryTest {
    private lateinit var db: LoopDatabase
    private lateinit var repo: LoopRepository
    private var now = 1_000_000L
    private val scheduled = mutableMapOf<Long, Long>()
    private val scheduler = object : LoopScheduler {
        override fun schedule(loopId: Long, at: Long) { scheduled[loopId] = at }
        override fun cancel(loopId: Long) { scheduled.remove(loopId) }
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

    @Test
    fun schedulerFollowsLifecycle() = runTest {
        val longTerm = repo.add("todo", null)
        val id = repo.add("task", now + 60_000)
        assertEquals(mapOf(id to now + 60_000), scheduled)

        now += 60_000
        repo.snooze(id, 5)
        assertEquals(mapOf(id to now + 5 * 60_000), scheduled)

        repo.close(id)
        assertEquals(emptyMap<Long, Long>(), scheduled)

        repo.delete(longTerm)
        assertEquals(emptyMap<Long, Long>(), scheduled)
    }

    @Test
    fun openLoopsSortedByDeadlineThenLongTerm() = runTest {
        repo.add("long-term", null)
        repo.add("later", now + 60 * 60_000)
        repo.add("soon", now + 5 * 60_000)

        val titles = repo.observeOpen().first().map { it.title }
        assertEquals(listOf("soon", "later", "long-term"), titles)
    }

    @Test
    fun addStoresOriginalDuration() = runTest {
        val id = repo.add("task", now + 45 * 60_000)
        assertEquals(45 * 60_000L, repo.get(id)!!.originalDurationMs)
        assertNull(repo.get(repo.add("todo", null))!!.originalDurationMs)
    }

    @Test
    fun editTitleOnlyKeepsDeadlineAndAlarm() = runTest {
        val id = repo.add("Bok flights", now + 60_000)
        now += 10_000
        repo.edit(id, "Book flights", null)

        val loop = repo.get(id)!!
        assertEquals("Book flights", loop.title)
        assertEquals(now - 10_000 + 60_000, loop.deadline)
        assertEquals(now - 10_000, loop.windowStart)
        assertEquals(mapOf(id to loop.deadline), scheduled)
    }

    @Test
    fun editWithNewDeadlineReactivatesAndReschedules() = runTest {
        val id = repo.add("task", now + 60_000)
        now += 120_000
        repo.markExpired(id)
        repo.edit(id, "task v2", now + 30 * 60_000)

        val loop = repo.get(id)!!
        assertEquals(LoopStatus.ACTIVE, loop.status)
        assertEquals(now + 30 * 60_000, loop.deadline)
        assertEquals(now, loop.windowStart)
        assertEquals(mapOf(id to now + 30 * 60_000), scheduled)
    }

    @Test
    fun editGivesLongTermLoopADeadline() = runTest {
        val id = repo.add("todo", null)
        repo.edit(id, "todo", now + 60_000)
        assertEquals(now + 60_000, repo.get(id)!!.deadline)
        assertEquals(mapOf(id to now + 60_000), scheduled)
    }

    @Test
    fun reopenRestoresActiveLoopAndAlarm() = runTest {
        val id = repo.add("task", now + 60_000)
        repo.close(id)
        repo.reopen(id)

        val loop = repo.get(id)!!
        assertEquals(LoopStatus.ACTIVE, loop.status)
        assertNull(loop.closedAt)
        assertEquals(mapOf(id to now + 60_000), scheduled)
    }

    @Test
    fun reopenPastDeadlineIsExpired() = runTest {
        val id = repo.add("task", now + 60_000)
        repo.close(id)
        now += 120_000
        repo.reopen(id)
        assertEquals(LoopStatus.EXPIRED, repo.get(id)!!.status)
    }

    @Test
    fun closedSinceCountsOnlyRecentCloses() = runTest {
        val a = repo.add("a", null)
        val b = repo.add("b", null)
        repo.add("c", null)
        repo.close(a)
        now += 1_000
        val since = now
        repo.close(b)
        assertEquals(1, repo.observeClosedSince(since).first())
        assertEquals(2, repo.observeClosedSince(0).first())
    }

    @Test
    fun closeRemovesFromOpenList() = runTest {
        val id = repo.add("task", now + 60_000)
        now += 10
        repo.close(id)

        assertEquals(emptyList<Loop>(), repo.observeOpen().first())
        assertEquals(LoopStatus.CLOSED, repo.get(id)!!.status)
        assertEquals(now, repo.get(id)!!.closedAt)
    }

    @Test
    fun snoozeResetsWindowAndReactivates() = runTest {
        val id = repo.add("task", now + 60_000)
        now += 60_000
        repo.markExpired(id)
        assertEquals(LoopStatus.EXPIRED, repo.get(id)!!.status)

        repo.snooze(id, 15)
        val loop = repo.get(id)!!
        assertEquals(LoopStatus.ACTIVE, loop.status)
        assertEquals(now + 15 * 60_000, loop.deadline)
        assertEquals(now, loop.windowStart)
        assertEquals(1, loop.snoozeCount)
    }

    @Test
    fun openWithDeadlineExcludesLongTermAndClosed() = runTest {
        repo.add("todo", null)
        val closed = repo.add("closed", now + 1)
        repo.close(closed)
        repo.add("active", now + 2)

        assertEquals(listOf("active"), repo.openWithDeadline().map { it.title })
    }
}
