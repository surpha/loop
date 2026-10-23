package com.closetheloop.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus
import com.closetheloop.domain.ExpiryPolicy
import com.closetheloop.loopApp
import kotlinx.coroutines.launch

/** Phone full-screen "Time's up" screen, shown over the lock screen when a loop expires. */
class PhoneExpiredActivity : ComponentActivity() {
    private var loopId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        loopId = intent.getLongExtra(EXTRA_LOOP_ID, -1L)
        val repository = loopApp.repository

        setContent {
            PhoneTheme {
                var loop by remember(loopId) { mutableStateOf<Loop?>(null) }
                val haptics = LocalHapticFeedback.current
                LaunchedEffect(loopId) {
                    repository.observe(loopId).collect {
                        // Resolved elsewhere (notification action, main screen): nothing left to show.
                        if (it == null || it.status != LoopStatus.EXPIRED) finish() else loop = it
                    }
                }
                loop?.let { current ->
                    ExpiredScreen(
                        loop = current,
                        onClose = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            lifecycleScope.launch { repository.close(current.id); finish() }
                        },
                        onSnooze = { m -> lifecycleScope.launch { repository.snooze(current.id, m); finish() } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        loopId = intent.getLongExtra(EXTRA_LOOP_ID, loopId)
    }

    companion object {
        private const val EXTRA_LOOP_ID = "loop_id"

        fun intent(context: Context, loopId: Long): Intent =
            Intent(context, PhoneExpiredActivity::class.java)
                .putExtra(EXTRA_LOOP_ID, loopId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun ExpiredScreen(loop: Loop, onClose: () -> Unit, onSnooze: (Int) -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    val overdueMs = System.currentTimeMillis() - (loop.deadline ?: 0L)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                drawCircle(
                    LoopColors.Red.copy(alpha = pulse),
                    radius = size.minDimension / 2 - stroke / 2,
                    style = Stroke(stroke),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(
                    if (overdueMs >= 60_000) "Overdue ${TimeFormat.remaining(overdueMs)}" else "Time's up",
                    style = MaterialTheme.typography.titleMedium,
                    color = LoopColors.Red,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    loop.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Close Loop", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpiryPolicy.SNOOZE_MINUTES.forEach { minutes ->
                FilledTonalButton(onClick = { onSnooze(minutes) }, modifier = Modifier.weight(1f)) {
                    Text("+$minutes min")
                }
            }
        }
    }
}
