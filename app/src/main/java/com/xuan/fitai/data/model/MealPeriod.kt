package com.xuan.fitai.data.model

import java.time.Instant
import java.time.ZoneId

object MealPeriod {
    val ordered = listOf("早餐", "午餐", "晚餐", "宵夜", "點心", "未分類")

    fun forHour(hour: Int): String {
        require(hour in 0..23)
        return when (hour) {
            in 5..10 -> "早餐"
            in 11..16 -> "午餐"
            in 17..21 -> "晚餐"
            else -> "宵夜"
        }
    }

    fun forTimestamp(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        forHour(Instant.ofEpochMilli(timestamp).atZone(zone).hour)
}
