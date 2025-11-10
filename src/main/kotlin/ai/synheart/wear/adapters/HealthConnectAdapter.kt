package ai.synheart.wear.adapters

import android.content.Context
import ai.synheart.wear.models.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Health Connect adapter for Android biometric data access
 *
 * Provides unified access to Health Connect data from multiple wearable devices.
 *
 * @param context Android application context
 */
class HealthConnectAdapter(
    private val context: Context
) : WearAdapter {

    override val id: String = "health_connect"

    private lateinit var healthConnectClient: HealthConnectClient

    override suspend fun initialize() {
        healthConnectClient = HealthConnectClient.getOrCreate(context)
    }

    override suspend fun requestPermissions(
        permissions: Set<PermissionType>
    ): Map<PermissionType, Boolean> {
        // Convert to Health Connect permissions
        val healthPermissions = permissions.mapNotNull { permType ->
            when (permType) {
                PermissionType.HEART_RATE -> HealthPermission.getReadPermission(HeartRateRecord::class)
                PermissionType.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
                else -> null
            }
        }.toSet()

        // Note: Actual permission request requires Activity context
        // This is a simplified implementation
        return permissions.associateWith { true }
    }

    override fun getPermissionStatus(): Map<PermissionType, Boolean> {
        // Simplified implementation
        return mapOf(
            PermissionType.HEART_RATE to true,
            PermissionType.HRV to true,
            PermissionType.STEPS to true,
            PermissionType.CALORIES to true
        )
    }

    override suspend fun readSnapshot(isRealTime: Boolean): WearMetrics {
        val now = Instant.now()
        val timeRange = TimeRangeFilter.between(
            now.minus(if (isRealTime) 1 else 60, ChronoUnit.MINUTES),
            now
        )

        // Read heart rate
        val hrRequest = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = timeRange
        )

        val hrResponse = try {
            healthConnectClient.readRecords(hrRequest)
        } catch (e: Exception) {
            null
        }

        val avgHr = hrResponse?.records?.lastOrNull()?.samples?.map { it.beatsPerMinute }
            ?.average() ?: 0.0

        // Read steps
        val stepsRequest = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = timeRange
        )

        val stepsResponse = try {
            healthConnectClient.readRecords(stepsRequest)
        } catch (e: Exception) {
            null
        }

        val totalSteps = stepsResponse?.records?.sumOf { it.count } ?: 0L

        return WearMetrics.builder()
            .timestamp(System.currentTimeMillis())
            .deviceId("healthconnect_${context.packageName.hashCode()}")
            .source("health_connect")
            .metric(MetricType.HR, avgHr)
            .metric(MetricType.STEPS, totalSteps.toDouble())
            .metaData("synced", "true")
            .build()
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }
}
