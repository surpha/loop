package com.closetheloop.domain

import com.closetheloop.data.Loop
import org.junit.Assert.assertEquals
import org.junit.Test

class LoopProgressTest {
    private fun loop(id: Long, created: Long, deadline: Long?, snoozedAt: Long? = null) =
        Loop(id = id, title = "t$id", createdAt = created, deadline = deadline, lastSnoozedAt = snoozedAt)

    @Test
    fun fractionCountsDownOverWindow() {
        val l = loop(1, created = 0, deadline = 1_000)
        assertEquals(1f, LoopProgress.fraction(l, 0), 0f)
        assertEquals(0.25f, LoopProgress.fraction(l, 750), 0.0001f)
        assertEquals(0f, LoopProgress.fraction(l, 1_000), 0f)
        assertEquals(0f, LoopProgress.fraction(l, 5_000), 0f)
    }

    @Test
    fun snoozeStartsNewWindow() {
        val l = loop(1, created = 0, deadline = 2_000, snoozedAt = 1_000)
        assertEquals(0.5f, LoopProgress.fraction(l, 1_500), 0.0001f)
    }

    @Test
    fun longTermHasNoProgress() = assertEquals(0f, LoopProgress.fraction(loop(1, 0, null), 0), 0f)

    @Test
    fun urgencyBands() {
        assertEquals(Urgency.CALM, LoopProgress.urgency(1f))
        assertEquals(Urgency.CALM, LoopProgress.urgency(0.51f))
        assertEquals(Urgency.WARNING, LoopProgress.urgency(0.5f))
        assertEquals(Urgency.WARNING, LoopProgress.urgency(0.2f))
        assertEquals(Urgency.CRITICAL, LoopProgress.urgency(0.19f))
        assertEquals(Urgency.CRITICAL, LoopProgress.urgency(0f))
    }

    @Test
    fun ringLoopsAreAllTickingSoonestFirst() {
        val loops = listOf(
            loop(1, 0, 500), loop(2, 0, null), loop(3, 0, 100),
            loop(4, 0, 900), loop(5, 0, 300), loop(6, 0, 1_000),
        )
        assertEquals(listOf(3L, 5L, 1L, 4L, 6L), LoopProgress.ringLoops(loops, now = 50).map { it.id })
        // Expired loops (3) drop off the bezel.
        assertEquals(listOf(5L, 1L, 4L, 6L), LoopProgress.ringLoops(loops, now = 100).map { it.id })
    }

    @Test
    fun noRingsWithoutTickingLoops() {
        val loops = listOf(loop(1, 0, null), loop(2, 0, 100))
        assertEquals(emptyList<Loop>(), LoopProgress.ringLoops(loops, now = 200))
    }
}
