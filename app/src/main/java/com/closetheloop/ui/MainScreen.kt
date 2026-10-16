package com.closetheloop.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TitleCard
import com.closetheloop.R
import com.closetheloop.data.Loop
import com.closetheloop.parser.ParsedTask
import com.closetheloop.voice.VoiceCapture
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel, startVoice: Boolean, onStartVoiceHandled: () -> Unit) {
    val context = LocalContext.current
    val voice = remember { VoiceCapture(context) }
    DisposableEffect(voice) { onDispose { voice.destroy() } }

    val voiceState by voice.state.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val loops by viewModel.openLoops.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) voice.start()
    }
    val listen: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(startVoice) {
        if (startVoice) {
            listen()
            onStartVoiceHandled()
        }
    }
    LaunchedEffect(voiceState) {
        (voiceState as? VoiceCapture.State.Heard)?.let {
            viewModel.save(it.text)
            voice.reset()
        }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    when (val state = voiceState) {
        is VoiceCapture.State.Listening -> ListeningScreen(state, onCancel = voice::cancel)
        is VoiceCapture.State.Failed -> FailedScreen(state.message, onRetry = listen, onDismiss = voice::reset)
        else -> {
            val task = saved
            if (task != null) {
                SavedScreen(task, onDone = viewModel::dismissSaved)
            } else {
                HomeScreen(loops, now, permissionDenied, onMic = listen)
            }
        }
    }
}

@Composable
private fun HomeScreen(loops: List<Loop>, now: Long, permissionDenied: Boolean, onMic: () -> Unit) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val zone = ZoneId.systemDefault()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item {
                FilledIconButton(onClick = onMic, modifier = Modifier.size(72.dp)) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = "Add loop", modifier = Modifier.size(36.dp))
                }
            }
            if (permissionDenied) {
                item {
                    Text(
                        "Microphone permission needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (loops.isEmpty()) {
                item {
                    Text(
                        "No open loops",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { ListHeader { Text("Open loops") } }
                items(loops, key = { it.id }) { loop ->
                    val deadline = loop.deadline
                    TitleCard(
                        onClick = {},
                        title = { Text(loop.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        time = { Text(if (deadline == null) "" else TimeFormat.remaining(deadline - now)) },
                        subtitle = {
                            Text(
                                if (deadline == null) "Long-term" else TimeFormat.clock(deadline, now, zone, is24h),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningScreen(state: VoiceCapture.State.Listening, onCancel: () -> Unit) {
    // rmsdB typically ranges from about -2 (silence) to 10 (loud).
    val level by animateFloatAsState(((state.rmsDb + 2f) / 12f).coerceIn(0f, 1f), label = "level")
    val primary = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(primary.copy(alpha = 0.12f + 0.25f * level), radius = r * (0.45f + 0.4f * level))
            drawCircle(primary, radius = r - 6.dp.toPx(), style = Stroke(width = 4.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                state.partial.ifBlank { "Listening…" },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SavedScreen(task: ParsedTask, onDone: () -> Unit) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val deadline = task.deadline
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(task) {
        delay(2_500)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        if (deadline != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(primary, radius = size.minDimension / 2 - 6.dp.toPx(), style = Stroke(width = 6.dp.toPx()))
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Text(
                if (deadline != null) "Loop set" else "Added to Long-Term",
                style = MaterialTheme.typography.labelMedium,
                color = primary,
            )
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (deadline != null) {
                val clock = TimeFormat.clock(deadline, now, ZoneId.systemDefault(), DateFormat.is24HourFormat(context))
                Text(
                    "$clock · ${TimeFormat.remaining(deadline - now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FailedScreen(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Try again") }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}
