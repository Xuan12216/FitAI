package com.xuan.fitai.data.model

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MealPeriodTest {
    @Test fun everyHourHasOneCategoryAndBoundariesAreConsistent() {
        val expected = List(5) { "宵夜" } + List(6) { "早餐" } + List(6) { "午餐" } + List(5) { "晚餐" } + List(2) { "宵夜" }
        assertEquals(expected, (0..23).map(MealPeriod::forHour))
        assertTrue(runCatching { MealPeriod.forHour(24) }.isFailure)
    }

    @Test fun selectedMealTimestampUsesLocalTimezoneRatherThanDeviceUtcHour() {
        val zone = ZoneId.of("Asia/Taipei")
        val timestamp = LocalDateTime.of(2026, 10, 23, 5, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("早餐", MealPeriod.forTimestamp(timestamp, zone))
        assertEquals("晚餐", MealPeriod.forTimestamp(timestamp, ZoneId.of("UTC")))
    }
}
