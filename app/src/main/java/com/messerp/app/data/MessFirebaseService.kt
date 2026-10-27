package com.messerp.app.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Receives the silent "sync" push, then fetches and shows the real notification from Supabase. */
class MessFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        try {
            runBlocking {
                withTimeoutOrNull(15_000) {
                    val store = SessionStore(applicationContext)
                    if (store.load() != null) PushRepository(AuthRepository(store)).registerToken(token)
                }
            }
        } catch (e: Exception) {
            // It will be registered again on the next login.
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            runBlocking { withTimeoutOrNull(15_000) { NotifySync.run(applicationContext) } }
        } catch (e: Exception) {
            // The periodic worker is the safety net.
        }
    }
}
