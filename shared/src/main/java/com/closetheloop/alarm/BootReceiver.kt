package com.closetheloop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.closetheloop.loopApp
import com.closetheloop.domain.ExpiryPolicy

/** Alarms don't survive reboot or app update: re-arm every open short-term loop. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = context.loopApp
        launchAsync {
            val now = System.currentTimeMillis()
            container.repository.openWithDeadline().forEach { loop ->
                ExpiryPolicy.rearmAt(loop, now)?.let { container.scheduler.schedule(loop.id, it) }
            }
        }
    }
}
