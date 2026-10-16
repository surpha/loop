package com.closetheloop

import android.app.Application
import android.content.Context
import com.closetheloop.alarm.LoopAlarms
import com.closetheloop.data.LoopDatabase
import com.closetheloop.data.LoopRepository
import com.closetheloop.data.LoopScheduler

class AppContainer(context: Context) {
    private val database = LoopDatabase.create(context)
    val scheduler: LoopScheduler = LoopAlarms(context)
    val repository = LoopRepository(database.loopDao(), scheduler)
}

class CloseTheLoopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CloseTheLoopApp).container
