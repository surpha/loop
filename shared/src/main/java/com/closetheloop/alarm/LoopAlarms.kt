package com.closetheloop.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.closetheloop.data.LoopScheduler

/** Exact expiry alarms via setAlarmClock (fires in Doze, wakes the device). */
class LoopAlarms(context: Context) : LoopScheduler {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(loopId: Long, at: Long) {
        val operation = fireIntent(loopId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            return
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        val show = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
    }

    override fun cancel(loopId: Long) {
        alarmManager.cancel(fireIntent(loopId))
        ExpiryAlert.dismiss(context, loopId)
    }

    private fun fireIntent(loopId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        loopId.toInt(),
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_LOOP_ID, loopId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
