package com.closetheloop.sync

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/** Watch side: signs into Firebase with the Google ID token the phone sends (or signs out). */
class AuthListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val auth = FirebaseAuth.getInstance()
        // Runs on a background binder thread, so blocking on the Firebase task is fine.
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchAuth.PATH }.forEach { event ->
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            if (map.getBoolean(WatchAuth.KEY_SIGNED_OUT)) {
                auth.signOut()
                Log.i(TAG, "signed out by phone")
                return@forEach
            }
            val token = map.getString(WatchAuth.KEY_ID_TOKEN) ?: return@forEach
            runCatching { Tasks.await(auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))) }
                .onSuccess { Log.i(TAG, "signed in from phone") }
                .onFailure { Log.w(TAG, "sign-in from phone failed", it) }
            // Firebase now holds its own session; don't leave the Google ID token in the Data Layer.
            runCatching { Tasks.await(Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri)) }
        }
    }

    private companion object {
        const val TAG = "AuthListener"
    }
}
