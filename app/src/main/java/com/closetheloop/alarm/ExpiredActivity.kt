package com.closetheloop.alarm

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.closetheloop.R
import com.closetheloop.appContainer
import com.closetheloop.data.Loop
import com.closetheloop.data.LoopStatus
import com.closetheloop.domain.ExpiryPolicy
import com.closetheloop.ui.LoopColors
import com.closetheloop.ui.TimeFormat
import kotlinx.coroutines.launch

/** Full-screen "time's up" screen shown over the lock screen when a loop expires. */
class ExpiredActivity : ComponentActivity() {
    private var loopId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        loopId = intent.getLongExtra(EXTRA_LOOP_ID, -1L)
        val repository = appContainer.repository

        setContent {
            MaterialTheme {
                AppScaffold {
                    var loop by remember(loopId) { mutableStateOf<Loop?>(null) }
                    LaunchedEffect(loopId) {
                        repository.observe(loopId).collect {
                            // Resolved elsewhere (notification action, dashboard): nothing left to show.
                            if (it == null || it.status != LoopStatus.EXPIRED) finish() else loop = it
                        }
                    }
                    loop?.let { current ->
                        ExpiredScreen(
                            loop = current,
                            onClose = { lifecycleScope.launch { repository.close(current.id); finish() } },
                            onSnooze = { m -> lifecycleScope.launch { repository.snooze(current.id, m); finish() } },
                        )
                    }
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
            Intent(context, ExpiredActivity::class.java)
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
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = {}, scrollIndicator = null) { padding ->
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                drawCircle(
                    LoopColors.Red.copy(alpha = pulse),
                    radius = size.minDimension / 2 - stroke / 2 - 3.dp.toPx(),
                    style = Stroke(stroke),
                )
            }
            ScalingLazyColumn(
                state = listState,
                autoCentering = AutoCenteringParams(itemIndex = 1),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        if (overdueMs >= 60_000) "Overdue ${TimeFormat.remaining(overdueMs)}" else "Time's up",
                        style = MaterialTheme.typography.labelMedium,
                        color = LoopColors.Red,
                    )
                }
                item {
                    Text(
                        loop.title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item {
                    Button(
                        onClick = onClose,
                        icon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                        colors = ButtonDefaults.buttonColors(containerColor = LoopColors.Green),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Close Loop") }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        ExpiryPolicy.SNOOZE_MINUTES.forEach { minutes ->
                            FilledTonalButton(
                                onClick = { onSnooze(minutes) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "+$minutes",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
