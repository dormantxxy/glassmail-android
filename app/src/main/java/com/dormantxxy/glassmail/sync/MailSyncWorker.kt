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

class MailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GlassMailApplication
        val accountId = inputData.getString(ACCOUNT_ID)
        return try {
            val count = if (accountId.isNullOrBlank()) app.repository.syncAll() else app.repository.syncAccount(accountId)
            if (count > 0) notifyNewMail(count)
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun notifyNewMail(count: Int) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, GlassMailApplication.CHANNEL_MAIL)
            .setSmallIcon(R.drawable.ic_notification_mail)
            .setContentTitle("收到 $count 封新邮件")
            .setContentText("点按打开澄邮")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
    }

    companion object { const val ACCOUNT_ID = "account_id" }
}
