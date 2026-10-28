package com.meowned.remind.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.meowned.remind.MeownedRemindApp
import com.meowned.remind.service.SmsForwarderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            val app = context.applicationContext as? MeownedRemindApp ?: MeownedRemindApp.instance
            val scope = CoroutineScope(Dispatchers.IO)

            scope.launch {
                try {
                    val isEnabled = app.preferences.isEnabledFlow.first()
                    if (isEnabled) {
                        SmsForwarderService.startService(context)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Error starting service after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
