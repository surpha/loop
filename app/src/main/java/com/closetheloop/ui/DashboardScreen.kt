package com.closetheloop.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.closetheloop.R
import com.closetheloop.data.Loop
import com.closetheloop.domain.LoopProgress
import java.time.ZoneId

@Composable
fun DashboardScreen(
    loops: List<Loop>,
    now: Long,
    permissionDenied: Boolean,
    onMic: () -> Unit,
    onClose: (Loop) -> Unit,
    onEditVoice: (Loop) -> Unit,
    onEditType: (Loop) -> Unit,
    onDelete: (Loop) -> Unit,
) {
    val shortTerm = loops.filter { it.deadline != null }.sortedBy { it.deadline }
    val longTerm = loops.filter { it.deadline == null }
    val ringLoops = LoopProgress.ringLoops(loops, now)
    val hero = shortTerm.firstOrNull()
    val upcoming = shortTerm.drop(1)
    val inset = ringsInset(ringLoops.size)

    var selected by remember { mutableStateOf<Loop?>(null) }
    var dialogLoop by remember { mutableStateOf<Loop?>(null) }
    val select: (Loop) -> Unit = { selected = it; dialogLoop = it }

    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = listState,
        // Rings occupy the bezel, so hide the curved clock and edge scroll indicator while they're shown.
        timeText = if (ringLoops.isEmpty()) null else ({}),
        scrollIndicator = null,
    ) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                state = listState,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                contentPadding = PaddingValues(
                    start = inset + 8.dp,
                    end = inset + 8.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Hero(
                        hero = hero,
                        hasLongTerm = longTerm.isNotEmpty(),
                        now = now,
                        moreCount = upcoming.size + longTerm.size,
                        permissionDenied = permissionDenied,
                        onMic = onMic,
                        onSelect = select,
                    )
                }
                if (upcoming.isNotEmpty()) {
                    item { ListHeader { Text("Upcoming") } }
                    items(upcoming, key = { it.id }) { LoopCard(it, now, onClick = { select(it) }) }
                }
                if (longTerm.isNotEmpty()) {
                    item { ListHeader { Text("Long-term") } }
                    items(longTerm, key = { it.id }) { LoopCard(it, now, onClick = { select(it) }) }
                }
            }
            LoopRings(
                rings = ringLoops.map {
                    val f = LoopProgress.fraction(it, now)
                    Ring(f, LoopColors.of(LoopProgress.urgency(f)))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    LoopActionsDialog(
        loop = dialogLoop,
        visible = selected != null,
        onDismiss = { selected = null },
        onClose = { selected?.let(onClose); selected = null },
        onEditVoice = { selected?.let(onEditVoice); selected = null },
        onEditType = { selected?.let(onEditType); selected = null },
        onDelete = { selected?.let(onDelete); selected = null },
    )
}

@Composable
private fun Hero(
    hero: Loop?,
    hasLongTerm: Boolean,
    now: Long,
    moreCount: Int,
    permissionDenied: Boolean,
    onMic: () -> Unit,
    onSelect: (Loop) -> Unit,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val deadline = hero?.deadline
        if (hero != null && deadline != null) {
            val fraction = LoopProgress.fraction(hero, now)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(hero) },
            ) {
                Text(
                    hero.title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // The hero's top sits where the round screen is narrowest.
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Text(
                    if (deadline <= now) "Time's up" else TimeFormat.remaining(deadline - now),
                    style = MaterialTheme.typography.titleLarge,
                    color = LoopColors.of(LoopProgress.urgency(fraction)),
                )
                Text(
                    TimeFormat.clock(deadline, now, ZoneId.systemDefault(), DateFormat.is24HourFormat(context)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            MicButton(onMic, size = 48)
        } else {
            Text(
                if (hasLongTerm) "Nothing ticking" else "No open loops",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            MicButton(onMic, size = 72)
        }
        if (permissionDenied) {
            Text(
                "Microphone permission needed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (moreCount > 0) {
            Text(
                "$moreCount more ▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit, size: Int) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(size.dp)) {
        Icon(
            painterResource(R.drawable.ic_mic),
            contentDescription = "Add loop",
            modifier = Modifier.size((size / 2).dp),
        )
    }
}

@Composable
private fun LoopCard(loop: Loop, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val deadline = loop.deadline
    TitleCard(
        onClick = onClick,
        title = { Text(loop.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        time = {
            if (deadline != null) {
                val urgency = LoopProgress.urgency(LoopProgress.fraction(loop, now))
                Text(TimeFormat.remaining(deadline - now), color = LoopColors.of(urgency))
            }
        },
        subtitle = {
            if (deadline != null) {
                Text(
                    TimeFormat.clock(deadline, now, ZoneId.systemDefault(), DateFormat.is24HourFormat(context)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LoopActionsDialog(
    loop: Loop?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onEditVoice: () -> Unit,
    onEditType: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        visible = visible && loop != null,
        onDismissRequest = onDismiss,
        title = { Text(loop?.title.orEmpty(), maxLines = 3, overflow = TextOverflow.Ellipsis) },
    ) {
        item {
            Button(
                onClick = onClose,
                icon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                colors = ButtonDefaults.buttonColors(containerColor = LoopColors.Green),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Close Loop") }
        }
        item {
            FilledTonalButton(
                onClick = onEditVoice,
                icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Edit by voice") }
        }
        item {
            FilledTonalButton(
                onClick = onEditType,
                icon = { Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Edit by typing") }
        }
        item {
            FilledTonalButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}
