package com.closetheloop.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.closetheloop.alarm.AlarmReceiver
import com.closetheloop.data.Loop
import com.closetheloop.domain.LiveCountdown
import com.closetheloop.domain.LiveCountdowns
import com.closetheloop.shared.R
import com.closetheloop.ui.LoopColors
import com.closetheloop.ui.TimeFormat
import java.time.ZoneId

/** Pinned countdown for the most urgent ticking loop (Android 16 Live Update when available). */
object LiveCountdownNotifier {
    private const val CHANNEL_ID = "live_countdown"
    // Outside the loop-id range used by expiry alerts.
    private const val NOTIFICATION_ID = Int.MAX_VALUE - 1
    private const val PROGRESS_MAX = 1000
    private const val SNOOZE_MINUTES = 15

    fun update(context: Context, loops: List<Loop>, now: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val live = LiveCountdowns.select(loops, now)
        if (live == null || !nm.areNotificationsEnabled()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel(nm)
        val notification = if (Build.VERSION.SDK_INT >= 36) liveUpdate(context, live, now) else compat(context, live, now)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun summary(context: Context, live: LiveCountdown, now: Long): String {
        val due = TimeFormat.clock(live.deadline, now, ZoneId.systemDefault(), DateFormat.is24HourFormat(context))
        val next = live.next?.let { " · Next: ${it.title} in ${TimeFormat.remaining(it.deadline!! - now)}" }.orEmpty()
        val more = if (live.moreCount > 0) " · +${live.moreCount} more" else ""
        return "Due $due$next$more"
    }

    private fun elapsed(live: LiveCountdown) = ((1f - live.fraction) * PROGRESS_MAX).toInt()

    @androidx.annotation.RequiresApi(36)
    private fun liveUpdate(context: Context, live: LiveCountdown, now: Long): Notification {
        val color = LoopColors.forLoop(live.loop.id).toArgb()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(PROGRESS_MAX).setColor(color)))
            .setProgress(elapsed(live))
        val icon = Icon.createWithResource(context, R.drawable.ic_loop_notification)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_loop_notification)
            .setContentTitle(live.loop.title)
            .setContentText(summary(context, live, now))
            .setStyle(style)
            .setColor(color)
            .setWhen(live.deadline)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            // Notification.EXTRA_REQUEST_PROMOTED_ONGOING; its builder setter isn't in the compileSdk 36 stubs.
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .setContentIntent(openApp(context))
            .addAction(Notification.Action.Builder(icon, "Close Loop", action(context, live.loop.id, AlarmReceiver.ACTION_CLOSE, 0)).build())
            .addAction(
                Notification.Action.Builder(
                    icon, "+$SNOOZE_MINUTES min", action(context, live.loop.id, AlarmReceiver.ACTION_SNOOZE, SNOOZE_MINUTES),
                ).build()
            )
            .build()
    }

    private fun compat(context: Context, live: LiveCountdown, now: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_loop_notification)
            .setContentTitle(live.loop.title)
            .setContentText(summary(context, live, now))
            .setColor(LoopColors.forLoop(live.loop.id).toArgb())
            .setProgress(PROGRESS_MAX, elapsed(live), false)
            .setWhen(live.deadline)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Close Loop", action(context, live.loop.id, AlarmReceiver.ACTION_CLOSE, 0))
            .addAction(0, "+$SNOOZE_MINUTES min", action(context, live.loop.id, AlarmReceiver.ACTION_SNOOZE, SNOOZE_MINUTES))
            .build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // Distinct request codes from ExpiryAlert's (loopId * 100 + minutes) so the two never overwrite each other.
    private fun action(context: Context, loopId: Long, action: String, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            -(loopId.toInt() * 100 + minutes + 1),
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmReceiver.EXTRA_LOOP_ID, loopId)
                .putExtra(AlarmReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live countdown", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pinned countdown for your most urgent loop"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }
        )
    }
}
