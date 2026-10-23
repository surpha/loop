package com.closetheloop.domain

import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopSummaryTest {
    private fun loop(id: Long, deadline: Long?, status: LoopStatus = LoopStatus.ACTIVE) =
        Loop(id = id, title = "t$id", createdAt = 0, deadline = deadline, status = status)

    @Test
    fun picksSoonestOpenShortTermLoop() {
        val loops = listOf(loop(1, 1_000), loop(2, null), loop(3, 400), loop(4, 100, LoopStatus.CLOSED))
        val summary = LoopSummaries.mostUrgent(loops, now = 200)!!
        assertEquals(3L, summary.loop.id)
        assertEquals(200L, summary.remainingMs)
        assertEquals(0.5f, summary.fraction, 0.0001f)
        assertEquals(Urgency.WARNING, summary.urgency)
    }

    @Test
    fun expiredLoopStillSurfaces() {
        val summary = LoopSummaries.mostUrgent(listOf(loop(1, 100, LoopStatus.EXPIRED)), now = 500)!!
        assertTrue(summary.expired)
        assertEquals(Urgency.CRITICAL, summary.urgency)
    }

    @Test
    fun noneWhenOnlyLongTerm() = assertNull(LoopSummaries.mostUrgent(listOf(loop(1, null)), now = 0))
}
