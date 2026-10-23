package com.closetheloop

import android.app.Application
import com.closetheloop.data.LoopDatabase
import com.closetheloop.data.LoopRepository

class CloseTheLoopPhoneApp : Application(), LoopApp {
    override val repository: LoopRepository by lazy { LoopRepository(LoopDatabase.create(this).loopDao()) }
}
