package com.messerp.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messerp.app.config.AppConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Shows every notification that has not yet been shown on this phone. Used by the worker and by instant push. */
object NotifySync {
    suspend fun run(ctx: Context): Int {
        if (!AppConfig.isSupabaseConfigured) return 0
        val store = SessionStore(ctx)
        if (store.load() == null) return 0
        if (!Notifier.canPost(ctx)) return 0
        val items = NotificationRepository(AuthRepository(store)).claim(20)
        for (n in items) Notifier.show(ctx, n)
        return items.size
    }
}

/** Runs about every 15 minutes (Android's minimum) as a safety net when instant push is not available. */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            NotifySync.run(applicationContext)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.success()
        }
    }
}

object NotifyScheduler {
    private const val NAME = "mess_notify"

    fun start(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
