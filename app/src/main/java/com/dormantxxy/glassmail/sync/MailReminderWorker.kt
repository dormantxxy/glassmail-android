package com.dormantxxy.glassmail.sync

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dormantxxy.glassmail.GlassMailApplication
import com.dormantxxy.glassmail.MainActivity
import com.dormantxxy.glassmail.R

class MailReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(MESSAGE_ID) ?: return Result.failure()
        val repository = (applicationContext as GlassMailApplication).repository
        val message = repository.message(id) ?: return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, GlassMailApplication.CHANNEL_MAIL)
            .setSmallIcon(R.drawable.ic_notification_mail)
            .setContentTitle("提醒我：${message.subject}")
            .setContentText(message.from.name.ifBlank { message.from.address })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        return Result.success()
    }

    companion object { const val MESSAGE_ID = "message_id" }
}
