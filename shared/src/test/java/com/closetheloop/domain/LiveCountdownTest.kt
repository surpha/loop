package com.closetheloop.domain

import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCountdownTest {
    private fun loop(id: Long, deadline: Long?, status: LoopStatus = LoopStatus.ACTIVE) =
        Loop(id = id, title = "t$id", createdAt = 0, deadline = deadline, status = status)

    @Test
    fun picksSoonestTickingWithNextAndMore() {
        val loops = listOf(loop(1, 900), loop(2, null), loop(3, 300), loop(4, 600), loop(5, 1_200))
        val live = LiveCountdowns.select(loops, now = 150)!!
        assertEquals(3L, live.loop.id)
        assertEquals(300L, live.deadline)
        assertEquals(0.5f, live.fraction, 0.0001f)
        assertEquals(4L, live.next?.id)
        assertEquals(2, live.moreCount)
    }

    @Test
    fun skipsExpiredAndPastDeadlines() {
        val loops = listOf(loop(1, 100, LoopStatus.EXPIRED), loop(2, 200), loop(3, 900))
        val live = LiveCountdowns.select(loops, now = 250)!!
        assertEquals(3L, live.loop.id)
        assertNull(live.next)
        assertEquals(0, live.moreCount)
    }

    @Test
    fun noneWhenNothingTicking() =
        assertNull(LiveCountdowns.select(listOf(loop(1, null), loop(2, 100, LoopStatus.EXPIRED)), now = 0))
}
