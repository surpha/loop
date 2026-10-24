package com.closetheloop.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Phone side: sends the Google ID token (or a sign-out) to the paired watch over the Data Layer. */
object WatchBridge {
    suspend fun sendSignIn(context: Context, idToken: String) = put(context) {
        putString(WatchAuth.KEY_ID_TOKEN, idToken)
        putBoolean(WatchAuth.KEY_SIGNED_OUT, false)
    }

    suspend fun sendSignOut(context: Context) = put(context) {
        remove(WatchAuth.KEY_ID_TOKEN)
        putBoolean(WatchAuth.KEY_SIGNED_OUT, true)
    }

    suspend fun connectedWatchName(context: Context): String? =
        runCatching { Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.displayName }.getOrNull()

    private suspend fun put(context: Context, fill: com.google.android.gms.wearable.DataMap.() -> Unit) {
        val request = PutDataMapRequest.create(WatchAuth.PATH).apply {
            dataMap.fill()
            dataMap.putLong(WatchAuth.KEY_SENT_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
