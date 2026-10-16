package com.closetheloop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.closetheloop.appContainer
import com.closetheloop.domain.ExpiryPolicy
import com.closetheloop.domain.ExpiryPolicy.Decision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles expiry alarms (fire/re-buzz) and notification actions (close/snooze). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val loopId = intent.getLongExtra(EXTRA_LOOP_ID, -1L).takeIf { it >= 0 } ?: return
        val container = context.appContainer
        val repository = container.repository

        launchAsync {
            when (intent.action) {
                ACTION_FIRE -> {
                    val now = System.currentTimeMillis()
                    when (val decision = ExpiryPolicy.onAlarm(repository.get(loopId), now)) {
                        Decision.Cancel -> container.scheduler.cancel(loopId)
                        is Decision.Wait -> container.scheduler.schedule(loopId, decision.at)
                        is Decision.Buzz -> {
                            if (decision.firstTime) repository.markExpired(loopId)
                            container.scheduler.schedule(loopId, decision.nextAt)
                            repository.get(loopId)?.let { ExpiryAlert.show(context, it) }
                        }
                    }
                }
                ACTION_CLOSE -> repository.close(loopId)
                ACTION_SNOOZE -> repository.snooze(loopId, intent.getIntExtra(EXTRA_MINUTES, 5))
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.closetheloop.action.FIRE"
        const val ACTION_CLOSE = "com.closetheloop.action.CLOSE"
        const val ACTION_SNOOZE = "com.closetheloop.action.SNOOZE"
        const val EXTRA_LOOP_ID = "loop_id"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Runs [block] off the main thread while keeping the broadcast alive. */
internal fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
