package com.closetheloop.domain

import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus
import com.closetheloop.domain.ExpiryPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryPolicyTest {
    private fun loop(deadline: Long?, status: LoopStatus = LoopStatus.ACTIVE) =
        Loop(id = 1, title = "t", createdAt = 0, deadline = deadline, status = status)

    @Test
    fun missingOrClosedOrLongTermCancels() {
        assertEquals(Decision.Cancel, ExpiryPolicy.onAlarm(null, 100))
        assertEquals(Decision.Cancel, ExpiryPolicy.onAlarm(loop(null), 100))
        assertEquals(Decision.Cancel, ExpiryPolicy.onAlarm(loop(50, LoopStatus.CLOSED), 100))
    }

    @Test
    fun earlyFireWaitsForDeadline() =
        assertEquals(Decision.Wait(60_000), ExpiryPolicy.onAlarm(loop(60_000), 0))

    @Test
    fun slightlyEarlyFireStillBuzzes() =
        assertEquals(Decision.Buzz(true, 500 + ExpiryPolicy.REBUZZ_INTERVAL_MS), ExpiryPolicy.onAlarm(loop(1_000), 500))

    @Test
    fun firstExpiryMarksAndRearms() =
        assertEquals(Decision.Buzz(true, 2_000 + ExpiryPolicy.REBUZZ_INTERVAL_MS), ExpiryPolicy.onAlarm(loop(1_000), 2_000))

    @Test
    fun alreadyExpiredRebuzzes() = assertEquals(
        Decision.Buzz(false, 9_000 + ExpiryPolicy.REBUZZ_INTERVAL_MS),
        ExpiryPolicy.onAlarm(loop(1_000, LoopStatus.EXPIRED), 9_000),
    )

    @Test
    fun rearmAt() {
        assertEquals(60_000L, ExpiryPolicy.rearmAt(loop(60_000), 0))
        assertEquals(10_000 + ExpiryPolicy.CATCH_UP_DELAY_MS, ExpiryPolicy.rearmAt(loop(1_000, LoopStatus.EXPIRED), 10_000))
        assertNull(ExpiryPolicy.rearmAt(loop(null), 0))
        assertNull(ExpiryPolicy.rearmAt(loop(1_000, LoopStatus.CLOSED), 0))
    }
}
