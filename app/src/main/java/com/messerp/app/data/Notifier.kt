package com.messerp.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.messerp.app.MainActivity
import com.messerp.app.R

/** Shows notifications in the phone's notification tray (local, no external push service). */
object Notifier {
    private const val CH_NORMAL = "mess_normal"
    private const val CH_IMPORTANT = "mess_important"
    private const val CH_EMERGENCY = "mess_emergency"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_NORMAL, "সাধারণ বিজ্ঞপ্তি", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_IMPORTANT, "গুরুত্বপূর্ণ বিজ্ঞপ্তি", NotificationManager.IMPORTANCE_HIGH))
        val emergency = NotificationChannel(CH_EMERGENCY, "জরুরি বিজ্ঞপ্তি", NotificationManager.IMPORTANCE_HIGH)
        emergency.enableVibration(true)
        nm.createNotificationChannel(emergency)
    }

    fun canPost(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun show(ctx: Context, n: AppNotification) {
        ensureChannels(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channel = when (n.level) {
            "EMERGENCY" -> CH_EMERGENCY
            "IMPORTANT" -> CH_IMPORTANT
            else -> CH_NORMAL
        }
        val text = Money.bn(n.body)
        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Money.bn(n.title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(if (n.level == "NORMAL") NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
        try {
            NotificationManagerCompat.from(ctx).notify(n.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission was revoked; the notification stays visible inside the app.
        }
    }
}
