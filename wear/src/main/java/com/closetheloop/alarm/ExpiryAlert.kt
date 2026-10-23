package com.closetheloop.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.closetheloop.R
import com.closetheloop.data.Loop
import com.closetheloop.domain.ExpiryPolicy

/** The "time's up" alert: full-screen notification with actions + a strong buzz. */
object ExpiryAlert {
    private const val CHANNEL_ID = "expired_loops"
    private val PATTERN = longArrayOf(0, 500, 150, 500, 150, 900)
    private val AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

    fun show(context: Context, loop: Loop) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(nm)
        val id = loop.id.toInt()
        val open = PendingIntent.getActivity(
            context, id, ExpiredActivity.intent(context, loop.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_loop_notification)
            .setContentTitle(loop.title)
            .setContentText("Time's up — close the loop")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Close Loop", action(context, loop.id, AlarmReceiver.ACTION_CLOSE, 0))
        ExpiryPolicy.SNOOZE_MINUTES.forEach { minutes ->
            builder.addAction(0, "+$minutes min", action(context, loop.id, AlarmReceiver.ACTION_SNOOZE, minutes))
        }

        if (nm.areNotificationsEnabled()) {
            // Re-post (not update) so the full-screen intent wakes the screen again on each re-buzz.
            nm.cancel(id)
            nm.notify(id, builder.build())
        }
        vibrate(context)
    }

    fun dismiss(context: Context, loopId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(loopId.toInt())
    }

    private fun action(context: Context, loopId: Long, action: String, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            loopId.toInt() * 100 + minutes,
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmReceiver.EXTRA_LOOP_ID, loopId)
                .putExtra(AlarmReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Expired loops", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Wakes the watch when a loop's time is up"
                // We drive the buzz ourselves so it can repeat with a strong pattern.
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createWaveform(PATTERN, AMPLITUDES, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
