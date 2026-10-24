package com.closetheloop.sync

/** Wear Data Layer contract for handing the phone's Google sign-in to the watch. */
object WatchAuth {
    const val PATH = "/closetheloop/auth"
    const val KEY_ID_TOKEN = "idToken"
    const val KEY_SIGNED_OUT = "signedOut"
    // Forces a data change even when the payload repeats.
    const val KEY_SENT_AT = "sentAt"
}
