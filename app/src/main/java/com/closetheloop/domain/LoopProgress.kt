package com.closetheloop.domain

import com.closetheloop.data.Loop

enum class Urgency { CALM, WARNING, CRITICAL }

object LoopProgress {
    const val MAX_RINGS = 3

    /** Remaining fraction of the current window: 1 = just started, 0 = expired. Long-term loops → 0. */
    fun fraction(loop: Loop, now: Long): Float {
        val deadline = loop.deadline ?: return 0f
        val total = deadline - loop.windowStart
        if (total <= 0) return 0f
        return ((deadline - now).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Green > 50%, Amber 20–50%, Red < 20%. */
    fun urgency(fraction: Float): Urgency = when {
        fraction > 0.5f -> Urgency.CALM
        fraction >= 0.2f -> Urgency.WARNING
        else -> Urgency.CRITICAL
    }

    /** The most urgent short-term loops, soonest first (outermost ring). */
    fun ringLoops(loops: List<Loop>): List<Loop> =
        loops.filter { it.deadline != null }.sortedBy { it.deadline }.take(MAX_RINGS)
}
