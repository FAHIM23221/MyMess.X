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

/** Runs about every 15 minutes (Android's minimum) and shows any new notifications while the app is closed. */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!AppConfig.isSupabaseConfigured) return Result.success()
        val store = SessionStore(applicationContext)
        if (store.load() == null) return Result.success()
        if (!Notifier.canPost(applicationContext)) return Result.success()
        return try {
            val repo = NotificationRepository(AuthRepository(store))
            for (n in repo.claim(20)) Notifier.show(applicationContext, n)
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
