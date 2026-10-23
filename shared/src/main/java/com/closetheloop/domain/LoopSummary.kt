package com.closetheloop.domain

import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus

/** What glanceable surfaces (Tile, Complication) show: the single most urgent short-term loop. */
data class LoopSummary(
    val loop: Loop,
    val remainingMs: Long,
    val fraction: Float,
    val urgency: Urgency,
) {
    val expired: Boolean get() = remainingMs <= 0
}

object LoopSummaries {
    fun mostUrgent(loops: List<Loop>, now: Long): LoopSummary? {
        val loop = loops
            .filter { it.deadline != null && it.status != LoopStatus.CLOSED }
            .minByOrNull { it.deadline!! }
            ?: return null
        val fraction = LoopProgress.fraction(loop, now)
        return LoopSummary(loop, loop.deadline!! - now, fraction, LoopProgress.urgency(fraction))
    }
}
