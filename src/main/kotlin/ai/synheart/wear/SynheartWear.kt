package ai.synheart.wear

import android.content.Context
import ai.synheart.wear.config.SynheartWearConfig
import ai.synheart.wear.models.*
import ai.synheart.wear.adapters.HealthConnectAdapter
import ai.synheart.wear.adapters.WearAdapter
import ai.synheart.wear.cache.LocalCache
import ai.synheart.wear.consent.ConsentManager
import ai.synheart.wear.normalization.Normalizer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay

/**
 * Main SynheartWear SDK class implementing RFC specifications
 *
 * Provides unified access to biometric data from multiple wearable devices
 * with standardized output format, encryption, and privacy controls.
 *
 * @param context Android application context
 * @param config SDK configuration
 */
class SynheartWear(
    private val context: Context,
    private val config: SynheartWearConfig = SynheartWearConfig()
) {
    private var initialized = false
    private val normalizer = Normalizer()
    private val consentManager = ConsentManager(context)
    private val localCache = LocalCache(context, config.enableEncryption)

    private val adapterRegistry: Map<DeviceAdapter, WearAdapter> by lazy {
        mapOf(
            DeviceAdapter.HEALTH_CONNECT to HealthConnectAdapter(context)
        )
    }

    /**
     * Initialize the SDK with permissions and setup
     *
     * This must be called before any other SDK methods.
     * Requests necessary permissions and initializes enabled adapters.
     *
     * @throws SynheartWearException if initialization fails
     */
    suspend fun initialize() {
        if (initialized) return

        try {
            // Initialize consent manager
            consentManager.initialize()

            // Initialize adapters
            enabledAdapters().forEach { adapter ->
                adapter.initialize()
            }

            initialized = true
        } catch (e: Exception) {
            throw SynheartWearException("Failed to initialize SynheartWear: ${e.message}", e)
        }
    }

    /**
     * Request specific permissions from the user
     *
     * @param permissions Set of permission types to request
     * @return Map of permission types to granted status
     */
    suspend fun requestPermissions(
        permissions: Set<PermissionType>
    ): Map<PermissionType, Boolean> {
        ensureInitialized()

        val results = mutableMapOf<PermissionType, Boolean>()

        enabledAdapters().forEach { adapter ->
            val adapterResults = adapter.requestPermissions(permissions)
            results.putAll(adapterResults)
        }

        return results
    }

    /**
     * Get current permission status
     *
     * @return Map of permission types to granted status
     */
    fun getPermissionStatus(): Map<PermissionType, Boolean> {
        ensureInitialized()

        val status = mutableMapOf<PermissionType, Boolean>()

        enabledAdapters().forEach { adapter ->
            status.putAll(adapter.getPermissionStatus())
        }

        return status
    }

    /**
     * Read current biometric metrics from all enabled adapters
     *
     * @param isRealTime Whether to read real-time data or historical snapshot
     * @return Unified WearMetrics containing all available biometric data
     * @throws SynheartWearException if metrics cannot be read
     */
    suspend fun readMetrics(isRealTime: Boolean = false): WearMetrics {
        ensureInitialized()

        try {
            // Validate consents
            consentManager.validateConsents(getRequiredPermissions())

            // Gather data from enabled adapters
            val adapterData = enabledAdapters().mapNotNull { adapter ->
                try {
                    adapter.readSnapshot(isRealTime)
                } catch (e: Exception) {
                    // Log but continue with other adapters
                    null
                }
            }

            // Normalize and merge data
            val mergedData = normalizer.mergeSnapshots(adapterData)

            // Validate data quality
            if (!normalizer.validateMetrics(mergedData)) {
                throw SynheartWearException("Invalid metrics data received")
            }

            // Cache data if enabled
            if (config.enableLocalCaching) {
                localCache.storeSession(mergedData)
            }

            return mergedData
        } catch (e: SynheartWearException) {
            throw e
        } catch (e: Exception) {
            throw SynheartWearException("Failed to read metrics: ${e.message}", e)
        }
    }

    /**
     * Stream real-time heart rate data
     *
     * @param intervalMs Polling interval in milliseconds (default: from config)
     * @return Flow of WearMetrics with updated HR data
     */
    fun streamHR(intervalMs: Long = config.streamInterval): Flow<WearMetrics> = flow {
        ensureInitialized()

        while (true) {
            try {
                val metrics = readMetrics(isRealTime = true)
                emit(metrics)
            } catch (e: Exception) {
                // Continue streaming even on errors
            }
            delay(intervalMs)
        }
    }

    /**
     * Stream HRV data in configurable windows
     *
     * @param windowMs Window size in milliseconds for HRV calculation
     * @return Flow of WearMetrics with updated HRV data
     */
    fun streamHRV(windowMs: Long = 5000L): Flow<WearMetrics> = flow {
        ensureInitialized()

        while (true) {
            try {
                val metrics = readMetrics(isRealTime = true)
                emit(metrics)
            } catch (e: Exception) {
                // Continue streaming even on errors
            }
            delay(windowMs)
        }
    }

    /**
     * Get cached biometric sessions
     *
     * @param startDateMs Start date in milliseconds since epoch
     * @param endDateMs End date in milliseconds since epoch (default: now)
     * @param limit Maximum number of sessions to return
     * @return List of cached WearMetrics
     */
    suspend fun getCachedSessions(
        startDateMs: Long,
        endDateMs: Long = System.currentTimeMillis(),
        limit: Int = 100
    ): List<WearMetrics> {
        ensureInitialized()
        return localCache.getSessions(startDateMs, endDateMs, limit)
    }

    /**
     * Get cache statistics
     *
     * @return Map containing cache statistics
     */
    suspend fun getCacheStats(): Map<String, Any> {
        ensureInitialized()
        return localCache.getStats()
    }

    /**
     * Clear old cached data
     *
     * @param maxAgeMs Maximum age of data to keep in milliseconds
     */
    suspend fun clearOldCache(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000) {
        ensureInitialized()
        localCache.clearOldData(maxAgeMs)
    }

    /**
     * Purge all cached data (GDPR compliance)
     */
    suspend fun purgeAllData() {
        ensureInitialized()
        localCache.purgeAll()
        consentManager.revokeAllConsents()
    }

    // Private helper methods

    private fun ensureInitialized() {
        if (!initialized) {
            throw SynheartWearException("SDK not initialized. Call initialize() first.")
        }
    }

    private fun enabledAdapters(): List<WearAdapter> {
        return config.enabledAdapters.mapNotNull { adapterRegistry[it] }
    }

    private fun getRequiredPermissions(): Set<PermissionType> {
        return setOf(
            PermissionType.HEART_RATE,
            PermissionType.HRV,
            PermissionType.STEPS,
            PermissionType.CALORIES
        )
    }
}

/**
 * Exception thrown by SynheartWear SDK
 */
class SynheartWearException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
