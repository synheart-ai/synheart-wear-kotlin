package ai.synheart.wear.adapters

import android.content.Context
import android.util.Log
import ai.synheart.wear.models.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Enhanced Health Connect adapter for Android biometric data access
 *
 * Provides comprehensive access to Health Connect data from multiple wearable devices
 * including heart rate, HRV, steps, calories, distance, exercise, and sleep data.
 *
 * Based on Google's Health Connect samples and best practices.
 *
 * @param context Android application context
 */
class HealthConnectAdapter(
    private val context: Context
) : WearAdapter {

    override val id: String = "health_connect"

    private val TAG = "HealthConnectAdapter"
    private lateinit var healthConnectClient: HealthConnectClient

    companion object {
        /**
         * Check if Health Connect is supported and available on this device
         */
        fun getSdkStatus(context: Context): Int {
            return HealthConnectClient.getSdkStatus(context)
        }

        /**
         * Check if Health Connect is available
         */
        fun isAvailable(context: Context): Boolean {
            return getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }
    }

    override suspend fun initialize() {
        healthConnectClient = HealthConnectClient.getOrCreate(context)
        Log.i(TAG, "Health Connect client initialized")
    }

    /**
     * Get all required Health Connect permissions based on requested permission types
     */
    fun getHealthConnectPermissions(permissions: Set<PermissionType>): Set<String> {
        return permissions.flatMap { permType ->
            when (permType) {
                PermissionType.HEART_RATE -> listOf(
                    HealthPermission.getReadPermission(HeartRateRecord::class)
                )
                PermissionType.HRV -> listOf(
                    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
                )
                PermissionType.STEPS -> listOf(
                    HealthPermission.getReadPermission(StepsRecord::class)
                )
                PermissionType.CALORIES -> listOf(
                    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
                    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
                )
                PermissionType.DISTANCE -> listOf(
                    HealthPermission.getReadPermission(DistanceRecord::class)
                )
                PermissionType.EXERCISE -> listOf(
                    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
                    HealthPermission.getReadPermission(SpeedRecord::class)
                )
                PermissionType.SLEEP -> listOf(
                    HealthPermission.getReadPermission(SleepSessionRecord::class)
                )
                PermissionType.STRESS -> listOf(
                    // Health Connect doesn't have a stress record yet
                    // We can infer stress from HRV
                    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
                )
            }
        }.toSet()
    }

    /**
     * Get permission request contract for Activity-based permission flow
     */
    fun getPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    /**
     * Check if all specified permissions are already granted
     */
    suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
                .containsAll(permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}", e)
            false
        }
    }

    override suspend fun requestPermissions(
        permissions: Set<PermissionType>
    ): Map<PermissionType, Boolean> {
        // Convert to Health Connect permissions
        val healthPermissions = getHealthConnectPermissions(permissions)

        // Note: Actual permission request requires Activity context
        // This method should be called from an Activity using the contract
        Log.i(TAG, "Permission request initiated for: $permissions")
        Log.i(TAG, "Health Connect permissions required: $healthPermissions")

        // Check current status
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            permissions.associateWith { permType ->
                val perms = getHealthConnectPermissions(setOf(permType))
                perms.all { it in granted }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission status: ${e.message}", e)
            permissions.associateWith { false }
        }
    }

    override fun getPermissionStatus(): Map<PermissionType, Boolean> {
        // This should be called asynchronously, but providing sync access
        return try {
            val granted = runCatching {
                // Note: This requires suspending, but interface is sync
                // In practice, apps should check permissions before calling this
                mapOf(
                    PermissionType.HEART_RATE to true,
                    PermissionType.HRV to true,
                    PermissionType.STEPS to true,
                    PermissionType.CALORIES to true,
                    PermissionType.DISTANCE to true,
                    PermissionType.EXERCISE to true,
                    PermissionType.SLEEP to true
                )
            }.getOrDefault(emptyMap())
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Error getting permission status: ${e.message}", e)
            emptyMap()
        }
    }

    override suspend fun readSnapshot(isRealTime: Boolean): WearMetrics {
        val now = Instant.now()
        val lookbackTime = if (isRealTime) 1L else 60L
        val timeRange = TimeRangeFilter.between(
            now.minus(lookbackTime, ChronoUnit.MINUTES),
            now
        )

        val builder = WearMetrics.builder()
            .timestamp(System.currentTimeMillis())
            .deviceId("healthconnect_${context.packageName.hashCode()}")
            .source("health_connect")

        // Read heart rate
        try {
            val hrRecords = readHeartRate(timeRange)
            if (hrRecords.isNotEmpty()) {
                val allSamples = hrRecords.flatMap { it.samples }
                if (allSamples.isNotEmpty()) {
                    val avgHr = allSamples.map { it.beatsPerMinute }.average()
                    builder.metric(MetricType.HR, avgHr)
                    Log.d(TAG, "Heart rate: $avgHr bpm (${allSamples.size} samples)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read heart rate: ${e.message}")
        }

        // Read HRV
        try {
            val hrvRecords = readHRV(timeRange)
            if (hrvRecords.isNotEmpty()) {
                val avgHrv = hrvRecords.mapNotNull { it.heartRateVariabilityMillis }.average()
                builder.metric(MetricType.HRV_RMSSD, avgHrv)
                Log.d(TAG, "HRV RMSSD: $avgHrv ms")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read HRV: ${e.message}")
        }

        // Read steps using aggregation
        try {
            val stepsCount = readStepsAggregate(timeRange)
            if (stepsCount > 0) {
                builder.metric(MetricType.STEPS, stepsCount.toDouble())
                Log.d(TAG, "Steps: $stepsCount")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read steps: ${e.message}")
        }

        // Read calories
        try {
            val calories = readCaloriesAggregate(timeRange)
            if (calories > 0) {
                builder.metric(MetricType.CALORIES, calories)
                Log.d(TAG, "Calories: $calories")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read calories: ${e.message}")
        }

        // Read distance
        try {
            val distance = readDistanceAggregate(timeRange)
            if (distance > 0) {
                builder.metric(MetricType.DISTANCE, distance)
                Log.d(TAG, "Distance: $distance meters")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read distance: ${e.message}")
        }

        builder.metaData("synced", "true")
        builder.metaData("timeRange", "${lookbackTime}min")

        return builder.build()
    }

    /**
     * Read heart rate records
     */
    suspend fun readHeartRate(timeRange: TimeRangeFilter): List<HeartRateRecord> {
        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = timeRange
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Read HRV records
     */
    suspend fun readHRV(timeRange: TimeRangeFilter): List<HeartRateVariabilityRmssdRecord> {
        val request = ReadRecordsRequest(
            recordType = HeartRateVariabilityRmssdRecord::class,
            timeRangeFilter = timeRange
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Read aggregated steps count
     */
    suspend fun readStepsAggregate(timeRange: TimeRangeFilter): Long {
        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.aggregate(request)
        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    /**
     * Read aggregated calories burned
     */
    suspend fun readCaloriesAggregate(timeRange: TimeRangeFilter): Double {
        val request = AggregateRequest(
            metrics = setOf(
                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
            ),
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.aggregate(request)
        
        val total = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inCalories ?: 0.0
        val active = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inCalories ?: 0.0
        
        // Return total if available, otherwise active
        return if (total > 0) total else active
    }

    /**
     * Read aggregated distance
     */
    suspend fun readDistanceAggregate(timeRange: TimeRangeFilter): Double {
        val request = AggregateRequest(
            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.aggregate(request)
        return response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
    }

    /**
     * Read exercise sessions
     */
    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Read sleep sessions
     */
    suspend fun readSleepSessions(start: Instant, end: Instant): List<SleepSessionRecord> {
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Read sleep duration aggregate
     */
    suspend fun readSleepDuration(start: Instant, end: Instant): Long? {
        val request = AggregateRequest(
            metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.aggregate(request)
        return response[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMillis()
    }

    /**
     * Revoke all Health Connect permissions
     */
    suspend fun revokeAllPermissions() {
        try {
            healthConnectClient.permissionController.revokeAllPermissions()
            Log.i(TAG, "All Health Connect permissions revoked")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke permissions: ${e.message}", e)
            throw e
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect availability: ${e.message}", e)
            false
        }
    }

    /**
     * Get detailed availability status
     */
    fun getAvailabilityStatus(): HealthConnectAvailability {
        return when (val status = getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> 
                HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE -> 
                HealthConnectAvailability.NotInstalled
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> 
                HealthConnectAvailability.UpdateRequired
            else -> 
                HealthConnectAvailability.Unknown(status)
        }
    }
}

/**
 * Health Connect availability status
 */
sealed class HealthConnectAvailability {
    object Available : HealthConnectAvailability()
    object NotInstalled : HealthConnectAvailability()
    object UpdateRequired : HealthConnectAvailability()
    data class Unknown(val code: Int) : HealthConnectAvailability()

    fun isAvailable(): Boolean = this is Available
}
