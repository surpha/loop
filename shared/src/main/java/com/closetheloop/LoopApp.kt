package com.closetheloop

import android.content.Context
import android.content.Intent
import com.closetheloop.data.LoopRepository
import com.closetheloop.data.LoopScheduler

/** Implemented by each app's Application so shared code (ViewModel factory, alarm receivers) can reach app services. */
interface LoopApp {
    val repository: LoopRepository
    val scheduler: LoopScheduler

    /** The app's full-screen "Time's up" screen for [loopId]. */
    fun expiredScreenIntent(context: Context, loopId: Long): Intent
}

val Context.loopApp: LoopApp get() = applicationContext as LoopApp
