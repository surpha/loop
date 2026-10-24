package com.closetheloop

import android.app.Application
import android.content.Context
import android.content.Intent
import com.closetheloop.alarm.ExpiredActivity
import com.closetheloop.alarm.LoopAlarms
import com.closetheloop.data.LoopDatabase
import com.closetheloop.data.LoopRepository
import com.closetheloop.data.LoopScheduler
import com.closetheloop.tile.LoopTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.closetheloop.sync.LoopSync

class AppContainer(context: Context) {
    private val database = LoopDatabase.create(context)
    val scheduler: LoopScheduler = LoopAlarms(context)
    val repository = LoopRepository(database.loopDao(), scheduler)
}

class CloseTheLoopApp : Application(), LoopApp {
    lateinit var container: AppContainer
        private set

    override val repository: LoopRepository get() = container.repository
    override val scheduler: LoopScheduler get() = container.scheduler

    override fun expiredScreenIntent(context: Context, loopId: Long): Intent = ExpiredActivity.intent(context, loopId)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sync: LoopSync by lazy { LoopSync(container.repository, appScope) }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        sync.start()
        // Keep glanceable surfaces in sync with any loop change (UI, alarms, notification actions).
        appScope.launch {
            container.repository.observeOpen().drop(1).collect { LoopTileService.requestUpdate(this@CloseTheLoopApp) }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CloseTheLoopApp).container
