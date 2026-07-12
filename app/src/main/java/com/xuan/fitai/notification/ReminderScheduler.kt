package com.xuan.fitai.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xuan.fitai.data.model.ReminderSettings
import java.util.Calendar

object ReminderScheduler {
    private const val MEAL_TYPE = "meal"
    private const val WATER_TYPE = "water"

    fun scheduleAll(context: Context, settings: ReminderSettings) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        settings.meals.forEachIndexed { index, meal ->
            val pendingIntent = reminderIntent(context, MEAL_TYPE, index, meal.label)
            alarmManager.cancel(pendingIntent)
            if (settings.mealRemindersEnabled && meal.enabled) {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, meal.hour)
                    set(Calendar.MINUTE, meal.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }

        val waterIntent = reminderIntent(context, WATER_TYPE, WATER_REQUEST_CODE, null)
        alarmManager.cancel(waterIntent)
        if (settings.waterRemindersEnabled) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextWaterTime(settings).timeInMillis,
                waterIntent
            )
        }
    }

    fun rescheduleAfterReminder(context: Context, settings: ReminderSettings) = scheduleAll(context, settings)

    fun scheduleWaterIn(context: Context, minutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val waterIntent = reminderIntent(context, WATER_TYPE, WATER_REQUEST_CODE, null)
        alarmManager.cancel(waterIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutes * 60_000L,
            waterIntent
        )
    }

    fun sendTestWaterReminder(context: Context) {
        context.sendBroadcast(
            Intent(context, ReminderNotificationReceiver::class.java)
                .setAction("com.xuan.fitai.REMINDER")
                .putExtra(ReminderNotificationReceiver.EXTRA_TYPE, WATER_TYPE)
        )
    }

    private fun nextWaterTime(settings: ReminderSettings): Calendar {
        val now = Calendar.getInstance()
        val next = now.clone() as Calendar
        val hour = now.get(Calendar.HOUR_OF_DAY)
        when {
            hour < settings.waterStartHour -> next.apply {
                set(Calendar.HOUR_OF_DAY, settings.waterStartHour)
                set(Calendar.MINUTE, 0)
            }
            hour >= settings.waterEndHour -> next.apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, settings.waterStartHour)
                set(Calendar.MINUTE, 0)
            }
            else -> next.add(Calendar.MINUTE, settings.waterIntervalMinutes)
        }
        if (next.get(Calendar.HOUR_OF_DAY) >= settings.waterEndHour && next.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
            next.add(Calendar.DAY_OF_YEAR, 1)
            next.set(Calendar.HOUR_OF_DAY, settings.waterStartHour)
            next.set(Calendar.MINUTE, 0)
        }
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        return next
    }

    private fun reminderIntent(context: Context, type: String, requestCode: Int, mealLabel: String?): PendingIntent {
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
            .setAction("com.xuan.fitai.REMINDER")
            .putExtra(ReminderNotificationReceiver.EXTRA_TYPE, type)
            .putExtra(ReminderNotificationReceiver.EXTRA_MEAL_LABEL, mealLabel)
        return PendingIntent.getBroadcast(context, requestCode + type.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val WATER_REQUEST_CODE = 100
}
