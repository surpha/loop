package com.closetheloop

import android.app.Application
import android.content.Context
import com.closetheloop.data.LoopDatabase
import com.closetheloop.data.LoopRepository

class AppContainer(context: Context) {
    private val database = LoopDatabase.create(context)
    val repository = LoopRepository(database.loopDao())
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
