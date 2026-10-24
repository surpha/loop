package com.closetheloop.sync

import android.util.Log
import com.closetheloop.data.LoopRepository
import com.closetheloop.data.LoopStatus
import com.closetheloop.data.RemoteLoop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Two-way sync between the local Room DB and Firestore at `users/{uid}/loops/{syncId}`.
 * Starts when a user is signed in, stops on sign-out. Room stays the source of truth for the UI.
 */
class LoopSync(
    private val repository: LoopRepository,
    private val scope: CoroutineScope,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    sealed interface State {
        data object SignedOut : State
        data class Syncing(val email: String?) : State
        data class Synced(val email: String?, val at: Long) : State
        data class Error(val email: String?, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    private var uploadJob: Job? = null
    private var listener: ListenerRegistration? = null

    fun start() {
        auth.addAuthStateListener { onUser(it.currentUser?.uid, it.currentUser?.email) }
    }

    private fun onUser(uid: String?, email: String?) {
        uploadJob?.cancel()
        listener?.remove()
        if (uid == null) {
            _state.value = State.SignedOut
            return
        }
        _state.value = State.Syncing(email)
        val loops = firestore.collection("users").document(uid).collection("loops")

        listener = loops.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "listen failed", error)
                _state.value = State.Error(email, error.message ?: "Sync error")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            // Echoes of our own unacknowledged writes are skipped per document, never per snapshot,
            // because documentChanges are diffs against the previous snapshot and would otherwise be lost.
            val changed = snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                .mapNotNull { it.document.toRemoteLoop() }
            scope.launch {
                changed.forEach { repository.applyRemote(it) }
                if (!snapshot.metadata.isFromCache) _state.value = State.Synced(email, System.currentTimeMillis())
            }
        }

        uploadJob = scope.launch {
            repository.observePendingUploads().collectLatest { pending ->
                for (remote in pending) {
                    try {
                        loops.document(remote.syncId).set(remote.toMap()).await()
                        repository.markUploaded(remote)
                    } catch (e: Exception) {
                        Log.w(TAG, "upload failed for ${remote.syncId}", e)
                        _state.value = State.Error(email, e.message ?: "Upload failed")
                    }
                }
                if (pending.isNotEmpty()) _state.value = State.Synced(email, System.currentTimeMillis())
            }
        }
    }

    private companion object {
        const val TAG = "LoopSync"
    }
}

internal fun RemoteLoop.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "createdAt" to createdAt,
    "deadline" to deadline,
    "originalDurationMs" to originalDurationMs,
    "status" to status.name,
    "snoozeCount" to snoozeCount,
    "lastSnoozedAt" to lastSnoozedAt,
    "closedAt" to closedAt,
    "updatedAt" to updatedAt,
    "deleted" to deleted,
)

internal fun DocumentSnapshot.toRemoteLoop(): RemoteLoop? {
    val title = getString("title") ?: return null
    return RemoteLoop(
        syncId = id,
        title = title,
        createdAt = getLong("createdAt") ?: return null,
        deadline = getLong("deadline"),
        originalDurationMs = getLong("originalDurationMs"),
        status = runCatching { LoopStatus.valueOf(getString("status") ?: "") }.getOrDefault(LoopStatus.ACTIVE),
        snoozeCount = getLong("snoozeCount")?.toInt() ?: 0,
        lastSnoozedAt = getLong("lastSnoozedAt"),
        closedAt = getLong("closedAt"),
        updatedAt = getLong("updatedAt") ?: 0,
        deleted = getBoolean("deleted") ?: false,
    )
}
