package com.closetheloop

import android.app.Application
import android.content.Context
import android.content.Intent
import com.closetheloop.alarm.LoopAlarms
import com.closetheloop.data.LoopDatabase
import com.closetheloop.data.LoopRepository
import com.closetheloop.data.LoopScheduler
import com.closetheloop.ui.PhoneExpiredActivity

class CloseTheLoopPhoneApp : Application(), LoopApp {
    override val scheduler: LoopScheduler by lazy { LoopAlarms(this) }
    override val repository: LoopRepository by lazy { LoopRepository(LoopDatabase.create(this).loopDao(), scheduler) }

    override fun expiredScreenIntent(context: Context, loopId: Long): Intent = PhoneExpiredActivity.intent(context, loopId)
}
