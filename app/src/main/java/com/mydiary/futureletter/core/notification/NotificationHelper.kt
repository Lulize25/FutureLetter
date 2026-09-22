package com.mydiary.futureletter.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mydiary.futureletter.MainActivity
import com.mydiary.futureletter.R
import com.mydiary.futureletter.core.database.entity.FutureLetter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_LETTERS = "future_letters"
        private const val NOTIFICATION_BASE_ID = 10000
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_LETTERS) == null) {
            val channel = NotificationChannel(
                CHANNEL_LETTERS,
                "未来信提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "未来信到达解锁时间时提醒你"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showLetterUnlocked(letter: FutureLetter) {
        ensureChannel()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, letter.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LETTERS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("📬 一封未来信已解锁")
            .setContentText(letter.title.ifBlank { "你写给未来自己的信" })
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_BASE_ID + letter.id.toInt(), notification)
    }

    /** 申请通知权限的入口（设置页用） */
    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
