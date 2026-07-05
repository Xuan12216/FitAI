package com.xuan.fitai.util

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class HealthData(
    val steps: Long = 0,
    val heartRate: Int = 0,
    val calories: Int = 0
)

class HealthConnectHelper(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    private val healthConnectClient: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    /**
     * Checks if the Health Connect SDK is available on the device.
     */
    fun isSdkAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Returns the raw SDK status code of Health Connect.
     */
    fun getSdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    /**
     * Checks if the required permissions are granted.
     */
    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Reads Health Connect data for today (from midnight to now).
     */
    suspend fun readTodayHealthData(): HealthData {
        val client = healthConnectClient ?: return HealthData()
        if (!hasAllPermissions()) return HealthData()

        try {
            val startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
            val now = Instant.now()

            val timeRangeFilter = TimeRangeFilter.between(startOfDay, now)

            // 1. Read Steps
            val stepsRequest = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val stepsResponse = client.readRecords(stepsRequest)
            val totalSteps = stepsResponse.records.sumOf { it.count }

            // 2. Read Heart Rate (calculate average heart rate)
            val heartRateRequest = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val heartRateResponse = client.readRecords(heartRateRequest)
            val allHeartRates = heartRateResponse.records.flatMap { record ->
                record.samples.map { it.beatsPerMinute }
            }
            val avgHeartRate = if (allHeartRates.isNotEmpty()) {
                allHeartRates.average().toInt()
            } else {
                0
            }

            // 3. Read Total Calories Burned
            val caloriesRequest = ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val caloriesResponse = client.readRecords(caloriesRequest)
            val totalCalories = caloriesResponse.records.sumOf { it.energy.inKilocalories }.toInt()

            return HealthData(
                steps = totalSteps,
                heartRate = avgHeartRate,
                calories = totalCalories
            )
        } catch (e: Exception) {
            android.util.Log.e("FitAI_HealthConnect", "Failed to read health data", e)
            return HealthData()
        }
    }
}
