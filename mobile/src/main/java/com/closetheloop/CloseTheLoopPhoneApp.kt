package com.closetheloop

import android.app.Application
import android.content.Context
import android.content.Intent
import com.closetheloop.alarm.LoopAlarms
import com.closetheloop.data.LoopDatabase
import com.closetheloop.data.LoopRepository
import com.closetheloop.data.LoopScheduler
import com.closetheloop.notify.LiveCountdownNotifier
import com.closetheloop.ui.PhoneExpiredActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class CloseTheLoopPhoneApp : Application(), LoopApp {
    override val scheduler: LoopScheduler by lazy { LoopAlarms(this) }
    override val repository: LoopRepository by lazy { LoopRepository(LoopDatabase.create(this).loopDao(), scheduler) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Refresh the pinned countdown on any loop change and once a minute (progress bar; the chronometer ticks itself).
        val minuteTicks = flow {
            while (true) {
                emit(Unit)
                delay(60_000)
            }
        }
        appScope.launch {
            combine(repository.observeOpen(), minuteTicks) { loops, _ -> loops }.collect { loops ->
                LiveCountdownNotifier.update(this@CloseTheLoopPhoneApp, loops, System.currentTimeMillis())
            }
        }
    }

    override fun expiredScreenIntent(context: Context, loopId: Long): Intent = PhoneExpiredActivity.intent(context, loopId)
}
