package com.xuan.fitai.data.model

data class MealReminder(
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

data class ReminderSettings(
    val mealRemindersEnabled: Boolean = false,
    val meals: List<MealReminder> = defaultMeals(),
    val waterRemindersEnabled: Boolean = false,
    val waterTargetMl: Int = 2000,
    val waterIntervalMinutes: Int = 60,
    val waterStartHour: Int = 8,
    val waterEndHour: Int = 22,
    val waterConsumedMl: Int = 0,
    val waterProgressDate: String = ""
) {
    companion object {
        fun defaultMeals() = listOf(
            MealReminder("早餐", 8, 0),
            MealReminder("午餐", 12, 30),
            MealReminder("晚餐", 18, 30)
        )
    }
}
