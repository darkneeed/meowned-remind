package com.meowned.remind

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.meowned.remind.data.db.AppDatabase
import com.meowned.remind.data.preferences.AppPreferences

class MeownedRemindApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferences = AppPreferences(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_SERVICE_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "meowned_remind_service_channel"
        const val NOTIFICATION_ID = 1001

        lateinit var instance: MeownedRemindApp
            private set
    }
}
