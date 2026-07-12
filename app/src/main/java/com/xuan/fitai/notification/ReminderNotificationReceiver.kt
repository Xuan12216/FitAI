package com.xuan.fitai.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xuan.fitai.FitAIApplication
import com.xuan.fitai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val type = intent.getStringExtra(EXTRA_TYPE)
                val mealLabel = intent.getStringExtra(EXTRA_MEAL_LABEL)
                val app = context.applicationContext as FitAIApplication
                when (intent.action) {
                    ACTION_ADD_WATER -> {
                        val current = app.reminderRepository.settings.first()
                        val today = LocalDate.now().toString()
                        val consumed = if (current.waterProgressDate == today) current.waterConsumedMl else 0
                        app.reminderRepository.save(current.copy(
                            waterConsumedMl = (consumed + 250).coerceAtMost(current.waterTargetMl),
                            waterProgressDate = today
                        ))
                        NotificationManagerCompat.from(context).cancel(WATER_NOTIFICATION_ID)
                        ReminderScheduler.scheduleAll(context, app.reminderRepository.settings.first())
                    }
                    ACTION_SNOOZE_WATER -> ReminderScheduler.scheduleWaterIn(context, 15)
                    else -> {
                        showNotification(context, type, mealLabel)
                        ReminderScheduler.rescheduleAfterReminder(context, app.reminderRepository.settings.first())
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, type: String?, mealLabel: String?) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val isMeal = type == "meal"
        val channelId = if (isMeal) MEAL_CHANNEL_ID else WATER_CHANNEL_ID
        val channelName = if (isMeal) "用餐提醒" else "喝水提醒"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH))

        val title = if (isMeal) "該吃${mealLabel ?: "飯"}囉" else "該喝水囉"
        val text = if (isMeal) "記錄今天的飲食，讓目標持續前進。" else "喝一杯水，補充 250 ml 水分。"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openRemindersIntent(context))
            .apply {
                if (!isMeal) {
                    addAction(0, "已喝 250 ml", actionIntent(context, ACTION_ADD_WATER))
                    addAction(0, "稍後提醒", actionIntent(context, ACTION_SNOOZE_WATER))
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(if (isMeal) (type + mealLabel).hashCode() else WATER_NOTIFICATION_ID, notification)
    }

    private fun openRemindersIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.xuan.fitai.MainActivity::class.java)
            .putExtra("open_reminders", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 300, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, ReminderNotificationReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_MEAL_LABEL = "meal_label"
        const val ACTION_ADD_WATER = "com.xuan.fitai.ADD_WATER"
        const val ACTION_SNOOZE_WATER = "com.xuan.fitai.SNOOZE_WATER"
        private const val WATER_NOTIFICATION_ID = 200
        private const val MEAL_CHANNEL_ID = "meal_reminders"
        private const val WATER_CHANNEL_ID = "water_reminders"
    }
}
