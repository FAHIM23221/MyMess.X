package com.messerp.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

object ChatSync {
    /**
     * Sends queued messages in order. The message id makes every retry safe (the server never stores one twice).
     * Throws IOException when the internet or server is unavailable, so the rest stays queued.
     */
    suspend fun flush(repo: ChatRepository, store: ChatStore, uid: String): List<ChatMessage> {
        val sent = mutableListOf<ChatMessage>()
        for (item in store.loadOutbox(uid)) {
            if (item.state != "QUEUED") continue
            try {
                val m = repo.send(item.conversationId, item.id, item.body)
                store.removeOutbox(uid, item.id)
                store.appendMessage(uid, item.conversationId, m)
                sent.add(m)
            } catch (e: ChatException) {
                store.setOutboxState(uid, item.id, "FAILED")
            }
        }
        return sent
    }
}

/** Runs once the phone has internet again, even if the app is closed. */
class ChatFlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val sessionStore = SessionStore(applicationContext)
        val session = sessionStore.load() ?: return Result.success()
        return try {
            ChatSync.flush(ChatRepository(AuthRepository(sessionStore)), ChatStore(applicationContext), session.userId)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.success()
        }
    }
}

object ChatScheduler {
    fun enqueue(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<ChatFlushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("chat_flush", ExistingWorkPolicy.REPLACE, request)
    }
}
