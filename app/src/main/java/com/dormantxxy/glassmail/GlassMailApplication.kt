package com.dormantxxy.glassmail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dormantxxy.glassmail.data.MailRepository

class GlassMailApplication : Application() {
    lateinit var repository: MailRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MailRepository(this)
        createNotificationChannels()
        repository.accounts.value.forEach(repository::schedulePeriodicSync)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SYNC, getString(R.string.notification_channel_sync), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_MAIL, getString(R.string.notification_channel_mail), NotificationManager.IMPORTANCE_DEFAULT)
            )
        )
    }

    companion object {
        const val CHANNEL_SYNC = "mail_sync"
        const val CHANNEL_MAIL = "new_mail"
    }
}
