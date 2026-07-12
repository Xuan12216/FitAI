package com.xuan.fitai.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xuan.fitai.FitAIApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FitAIApplication
                ReminderScheduler.scheduleAll(context, app.reminderRepository.settings.first())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
