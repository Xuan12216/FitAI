package com.xuan.fitai.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.data.model.MealReminder
import com.xuan.fitai.data.model.ReminderSettings
import com.xuan.fitai.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderViewModel(private val repository: ReminderRepository) : ViewModel() {
    val settings: StateFlow<ReminderSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch { repository.save(transform(settings.value)) }
    }

    fun setMealCount(count: Int) = update { current ->
        current.copy(meals = defaultMeals(count))
    }

    fun addWater(amountMl: Int) = update { current ->
        val today = LocalDate.now().toString()
        val consumed = if (current.waterProgressDate == today) current.waterConsumedMl else 0
        current.copy(
            waterConsumedMl = (consumed + amountMl).coerceAtMost(current.waterTargetMl),
            waterProgressDate = today
        )
    }

    private fun defaultMeals(count: Int): List<MealReminder> = when (count) {
        2 -> listOf(MealReminder("早午餐", 10, 0), MealReminder("晚餐", 18, 30))
        3 -> ReminderSettings.defaultMeals()
        4 -> listOf(MealReminder("早餐", 8, 0), MealReminder("午餐", 12, 30), MealReminder("下午點心", 15, 30), MealReminder("晚餐", 18, 30))
        else -> listOf(MealReminder("早餐", 8, 0), MealReminder("上午點心", 10, 30), MealReminder("午餐", 12, 30), MealReminder("下午點心", 15, 30), MealReminder("晚餐", 18, 30))
    }

    class Factory(private val repository: ReminderRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReminderViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ReminderViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
