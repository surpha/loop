package com.closetheloop.domain

import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus

/** Content of the phone's pinned live countdown: the soonest loop still ticking, plus what's after it. */
data class LiveCountdown(
    val loop: Loop,
    val deadline: Long,
    /** Remaining fraction of the current window (1 = full, 0 = due). */
    val fraction: Float,
    val next: Loop?,
    /** Ticking loops beyond [loop] and [next]. */
    val moreCount: Int,
)

object LiveCountdowns {
    fun select(loops: List<Loop>, now: Long): LiveCountdown? {
        val ticking = loops
            .filter { it.status == LoopStatus.ACTIVE && (it.deadline ?: return@filter false) > now }
            .sortedBy { it.deadline }
        val first = ticking.firstOrNull() ?: return null
        return LiveCountdown(
            loop = first,
            deadline = first.deadline!!,
            fraction = LoopProgress.fraction(first, now),
            next = ticking.getOrNull(1),
            moreCount = (ticking.size - 2).coerceAtLeast(0),
        )
    }
}
