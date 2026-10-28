package com.meowned.remind.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meowned.remind.MainActivity
import com.meowned.remind.MeownedRemindApp
import com.meowned.remind.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class SmsForwarderService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification(getString(R.string.service_running_text))
        observeStats()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MeownedRemindApp.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(MeownedRemindApp.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MeownedRemindApp.CHANNEL_SERVICE_ID)
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun observeStats() {
        val app = application as? MeownedRemindApp ?: return
        val dao = app.database.smsLogDao()

        serviceScope.launch {
            combine(dao.getSuccessCount(), dao.getFailedCount()) { success, failed ->
                "Переслано: $success | Ошибок: $failed"
            }.collect { text ->
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.notify(MeownedRemindApp.NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_STOP = "com.meowned.remind.action.STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, SmsForwarderService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SmsForwarderService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
