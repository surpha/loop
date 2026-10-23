package com.closetheloop.data

/** Arms/disarms the expiry alarm for a loop. Implemented by `alarm.LoopAlarms`. */
interface LoopScheduler {
    fun schedule(loopId: Long, at: Long)
    fun cancel(loopId: Long)

    object None : LoopScheduler {
        override fun schedule(loopId: Long, at: Long) = Unit
        override fun cancel(loopId: Long) = Unit
    }
}
