package com.closetheloop.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.closetheloop.data.Loop
import com.closetheloop.mobile.R
import com.closetheloop.sync.LoopSync
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Header avatar: person icon when signed out, the account's initial when signed in. */
@Composable
fun AccountButton(state: LoopSync.State, onClick: () -> Unit) {
    val email = state.email()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (email != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (email != null) {
                Text(
                    email.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(painterResource(R.drawable.ic_person), contentDescription = "Account", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    state: LoopSync.State,
    busy: Boolean,
    onSignIn: () -> Unit,
    onConnectWatch: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text("Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val email = state.email()
            if (email == null) {
                Text(
                    "Sign in to sync your loops with your watch. Without an account everything stays on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                val user = FirebaseAuth.getInstance().currentUser
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountButton(state, onClick = {})
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        user?.displayName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                SyncStatus(state)
                Button(onClick = onConnectWatch, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Connect watch")
                    }
                }
                Text(
                    "Signs your watch into the same account so loops sync both ways.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                Text(
                    "Signing out stops syncing; loops already on this phone stay here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SyncStatus(state: LoopSync.State) {
    val (dot, text) = when (state) {
        is LoopSync.State.Synced -> LoopColors.Green to "Synced · ${DateUtils.getRelativeTimeSpanString(state.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
        is LoopSync.State.Syncing -> MaterialTheme.colorScheme.primary to "Syncing…"
        is LoopSync.State.Error -> LoopColors.Red to "Sync problem: ${state.message}"
        LoopSync.State.SignedOut -> Color.Gray to "Not syncing"
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosedTodaySheet(loops: List<Loop>, onReopen: (Loop) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val timeFormat = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 24.dp)) {
            Text("Closed today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (loops.isEmpty()) {
                Text("Nothing closed yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(loops, key = { it.id }) { loop ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("✓", color = LoopColors.Green, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(loop.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            loop.closedAt?.let {
                                Text(
                                    "Closed ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onReopen(loop) }) { Text("Reopen") }
                    }
                }
            }
        }
    }
}

private fun LoopSync.State.email(): String? = when (this) {
    is LoopSync.State.Synced -> email
    is LoopSync.State.Syncing -> email
    is LoopSync.State.Error -> email
    LoopSync.State.SignedOut -> null
}
