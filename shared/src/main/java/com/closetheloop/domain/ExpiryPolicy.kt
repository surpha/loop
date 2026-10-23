package com.closetheloop.domain

import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus

object ExpiryPolicy {
    const val REBUZZ_INTERVAL_MS = 3 * 60_000L
    val SNOOZE_MINUTES = listOf(5, 15, 30)
    /** Delay before re-firing loops that were already due when alarms are re-armed (boot/update). */
    const val CATCH_UP_DELAY_MS = 5_000L
    private const val EARLY_TOLERANCE_MS = 1_000L

    sealed interface Decision {
        /** Loop gone, closed, or long-term: drop the alarm. */
        data object Cancel : Decision
        /** Fired before the (possibly snoozed) deadline: re-arm for it. */
        data class Wait(val at: Long) : Decision
        /** Due: buzz now and re-arm. [firstTime] = loop still ACTIVE and must be marked EXPIRED. */
        data class Buzz(val firstTime: Boolean, val nextAt: Long) : Decision
    }

    fun onAlarm(loop: Loop?, now: Long): Decision {
        val deadline = loop?.deadline
        if (loop == null || deadline == null || loop.status == LoopStatus.CLOSED) return Decision.Cancel
        if (deadline > now + EARLY_TOLERANCE_MS) return Decision.Wait(deadline)
        return Decision.Buzz(firstTime = loop.status == LoopStatus.ACTIVE, nextAt = now + REBUZZ_INTERVAL_MS)
    }

    /** When to arm a loop's alarm after boot/update. */
    fun rearmAt(loop: Loop, now: Long): Long? {
        val deadline = loop.deadline ?: return null
        if (loop.status == LoopStatus.CLOSED) return null
        return maxOf(deadline, now + CATCH_UP_DELAY_MS)
    }
}
