package com.closetheloop.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.closetheloop.data.Loop
import com.closetheloop.domain.ExpiryPolicy
import com.closetheloop.domain.LoopProgress
import com.closetheloop.domain.QuickTime
import com.closetheloop.domain.Urgency
import com.closetheloop.mobile.R
import com.closetheloop.parser.TaskParser
import com.closetheloop.voice.VoiceCapture
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EXAMPLES = listOf("Call mom in 20 min", "Study 2 hrs", "Gym tomorrow at 7", "Buy milk")

/** Where the next speech result goes: straight to a new loop, or into the composer's text field. */
private enum class VoiceTarget { SAVE, COMPOSER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val loops by viewModel.openLoops.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val closedToday by viewModel.closedToday.collectAsStateWithLifecycle()
    val voice = remember { VoiceCapture(context) }
    DisposableEffect(voice) { onDispose { voice.destroy() } }
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val is24h = DateFormat.is24HourFormat(context)
    val zone = ZoneId.systemDefault()

    var selected by remember { mutableStateOf<Loop?>(null) }
    var editing by remember { mutableStateOf<Loop?>(null) }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    var composerText by rememberSaveable { mutableStateOf("") }
    var voiceTarget by rememberSaveable { mutableStateOf(VoiceTarget.SAVE) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start()
    }
    val listen: (VoiceTarget) -> Unit = { target ->
        voiceTarget = target
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val openComposer: (String) -> Unit = {
        composerText = it
        composerOpen = true
    }
    val closeWithUndo: (Loop) -> Unit = { loop ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        viewModel.close(loop.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Closed · ${loop.title}", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.reopen(loop.id)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceCapture.State.Heard -> {
                if (voiceTarget == VoiceTarget.COMPOSER) composerText = state.text else viewModel.save(state.text)
                voice.reset()
            }
            is VoiceCapture.State.Failed -> {
                voice.reset()
                snackbar.showSnackbar(state.message)
            }
            else -> Unit
        }
    }
    LaunchedEffect(saved) {
        val confirmation = saved ?: return@LaunchedEffect
        viewModel.dismissSaved()
        val deadline = confirmation.task.deadline
        val label = when {
            confirmation.edited -> "Updated"
            deadline != null -> "Loop set"
            else -> "Added to Long-term"
        }
        val time = deadline?.let { " · ${TimeFormat.clock(it, System.currentTimeMillis(), zone, is24h)}" }.orEmpty()
        snackbar.showSnackbar("$label · ${confirmation.task.title}$time")
    }

    val shortTerm = loops.filter { it.deadline != null }.sortedBy { it.deadline }
    val overdue = shortTerm.filter { it.deadline!! <= now }
    val upcoming = shortTerm.filter { it.deadline!! > now }
    val longTerm = loops.filter { it.deadline == null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { AddBar(onCompose = { openComposer("") }, onMic = { listen(VoiceTarget.SAVE) }) },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item { Header(now, zone, closedToday) }
            item { RingsHero(loops, shortTerm.firstOrNull(), now, is24h, onSelect = { selected = it }) }
            if (loops.isEmpty()) {
                item { EmptyState(onExample = openComposer) }
            }
            section("Time's up", overdue, now, is24h, onClick = { selected = it }, onSwipeClose = closeWithUndo)
            section("Upcoming", upcoming, now, is24h, onClick = { selected = it }, onSwipeClose = closeWithUndo)
            section("Long-term", longTerm, now, is24h, onClick = { selected = it }, onSwipeClose = closeWithUndo)
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    (voiceState as? VoiceCapture.State.Listening)?.let { state ->
        AlertDialog(
            onDismissRequest = voice::cancel,
            confirmButton = { TextButton(onClick = voice::cancel) { Text("Cancel") } },
            icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(state.partial.ifBlank { "Listening…" }, textAlign = TextAlign.Center) },
        )
    }

    if (composerOpen) {
        Composer(
            text = composerText,
            onTextChange = { composerText = it },
            now = now,
            is24h = is24h,
            onMic = { listen(VoiceTarget.COMPOSER) },
            onDismiss = { composerOpen = false },
            onAdd = { title, deadline ->
                viewModel.add(title, deadline)
                composerText = ""
                composerOpen = false
            },
        )
    }

    selected?.let { loop ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            LoopActions(
                loop = loop,
                now = now,
                is24h = is24h,
                onClose = {
                    closeWithUndo(loop)
                    selected = null
                },
                onSnooze = { minutes ->
                    viewModel.snooze(loop.id, minutes)
                    selected = null
                },
                onEdit = {
                    editing = loop
                    selected = null
                },
                onDelete = {
                    viewModel.delete(loop.id)
                    selected = null
                },
            )
        }
    }

    editing?.let { loop ->
        EditDialog(
            loop = loop,
            onDismiss = { editing = null },
            onSave = { text ->
                viewModel.edit(loop.id, text)
                editing = null
            },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    loops: List<Loop>,
    now: Long,
    is24h: Boolean,
    onClick: (Loop) -> Unit,
    onSwipeClose: (Loop) -> Unit,
) {
    if (loops.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, loops.size) }
    items(loops, key = { it.id }) { loop ->
        SwipeToClose(onClose = { onSwipeClose(loop) }) {
            LoopCard(loop, now, is24h, onClick = { onClick(loop) })
        }
    }
}

@Composable
private fun Header(now: Long, zone: ZoneId, closedToday: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("CloseTheLoop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                Instant.ofEpochMilli(now).atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (closedToday > 0) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "✓ $closedToday closed today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RingsHero(loops: List<Loop>, hero: Loop?, now: Long, is24h: Boolean, onSelect: (Loop) -> Unit) {
    val ringLoops = LoopProgress.ringLoops(loops, now)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp).aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        LoopRings(
            rings = ringLoops.map {
                val fraction = LoopProgress.fraction(it, now)
                val urgency = LoopProgress.urgency(fraction)
                Ring(fraction, LoopColors.display(it.id, urgency), urgency == Urgency.CRITICAL)
            },
            modifier = Modifier.fillMaxSize(),
        )
        val deadline = hero?.deadline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = ringsInset(ringLoops.size) + 24.dp)
                .then(if (hero != null) Modifier.clickable { onSelect(hero) } else Modifier),
        ) {
            if (hero == null || deadline == null) {
                Text(
                    if (loops.isEmpty()) "No open loops" else "Nothing ticking",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val urgency = LoopProgress.urgency(LoopProgress.fraction(hero, now))
                Text(
                    hero.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (deadline <= now) "Time's up" else TimeFormat.remaining(deadline - now),
                    style = MaterialTheme.typography.displaySmall,
                    color = LoopColors.display(hero.id, urgency),
                )
                Text(
                    TimeFormat.clock(deadline, now, ZoneId.systemDefault(), is24h),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onExample: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Text(
            "Say or type a task with a time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            EXAMPLES.forEach { SuggestionChip(onClick = { onExample(it) }, label = { Text(it) }) }
        }
    }
}

@Composable
private fun SectionHeader(text: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToClose(onClose: () -> Unit, content: @Composable () -> Unit) {
    @Suppress("DEPRECATION")
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd) onClose()
            it == SwipeToDismissBoxValue.StartToEnd
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        modifier = Modifier.padding(horizontal = 16.dp),
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
                    .padding(start = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("✓ Close loop", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { content() }
}

@Composable
private fun LoopCard(loop: Loop, now: Long, is24h: Boolean, onClick: () -> Unit) {
    val deadline = loop.deadline
    val fraction = LoopProgress.fraction(loop, now)
    val urgency = LoopProgress.urgency(fraction)
    val color = if (deadline != null) LoopColors.display(loop.id, urgency) else MaterialTheme.colorScheme.outline
    val zone = ZoneId.systemDefault()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (deadline != null) 12.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (deadline != null) LoopColors.forLoop(loop.id) else color, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(loop.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            deadline == null -> "No deadline"
                            deadline <= now -> "Overdue ${TimeFormat.remaining(now - deadline)} · ${TimeFormat.clock(deadline, now, zone, is24h)}"
                            else -> TimeFormat.clock(deadline, now, zone, is24h)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deadline != null && deadline <= now) LoopColors.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (deadline != null && deadline > now) {
                    Text(
                        TimeFormat.remaining(deadline - now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
            }
            if (deadline != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    color = color,
                    trackColor = color.copy(alpha = 0.15f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
        }
    }
}

@Composable
private fun AddBar(onCompose: () -> Unit, onMic: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            onClick = onCompose,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("What do you need to do?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledIconButton(onClick = onMic, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = "Speak a loop")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    now: Long,
    is24h: Boolean,
    onMic: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (title: String, deadline: Long?) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var chip by rememberSaveable { mutableStateOf<QuickTime?>(null) }
    val parsed = remember(text, now / 60_000) { TaskParser.parse(text.trim(), now, zone) }
    // A chip overrides any time typed in the text.
    val deadline = chip?.deadline(now, zone) ?: parsed.deadline.takeIf { chip == null }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
        ) {
            Text("New loop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("e.g. Send the report in 45 minutes") },
                trailingIcon = {
                    IconButton(onClick = onMic) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = "Dictate", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            if (text.isNotBlank()) {
                ParsePreview(parsed.title, deadline, now, is24h)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTime.entries.forEach { option ->
                    FilterChip(
                        selected = chip == option,
                        onClick = { chip = if (chip == option) null else option },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Button(
                onClick = { onAdd(parsed.title, deadline) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Add loop", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ParsePreview(title: String, deadline: Long?, now: Long, is24h: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (deadline == null) "Long-term · no alarm" else "⏱ ${TimeFormat.clock(deadline, now, ZoneId.systemDefault(), is24h)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (deadline != null) {
                Text(
                    "in ${TimeFormat.remaining(deadline - now)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LoopActions(
    loop: Loop,
    now: Long,
    is24h: Boolean,
    onClose: () -> Unit,
    onSnooze: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val deadline = loop.deadline
    val zone = ZoneId.systemDefault()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (deadline != null) {
                Box(Modifier.size(12.dp).background(LoopColors.forLoop(loop.id), CircleShape))
                Spacer(Modifier.width(10.dp))
            }
            Text(loop.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(
            when {
                deadline == null -> "Long-term · no deadline"
                deadline <= now -> "Time's up · ${TimeFormat.clock(deadline, now, zone, is24h)}"
                else -> "${TimeFormat.clock(deadline, now, zone, is24h)} · ${TimeFormat.remaining(deadline - now)} left"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Close Loop", fontWeight = FontWeight.SemiBold)
        }
        if (deadline != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ExpiryPolicy.SNOOZE_MINUTES.forEach { minutes ->
                    FilledTonalButton(onClick = { onSnooze(minutes) }, modifier = Modifier.weight(1f)) {
                        Text("+$minutes min")
                    }
                }
            }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit") }
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete") }
    }
}

@Composable
private fun EditDialog(loop: Loop, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(loop.id) { mutableStateOf(loop.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit loop") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it })
                Text(
                    "Add a time (e.g. \"by 6pm\") to change the deadline; otherwise only the title changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
