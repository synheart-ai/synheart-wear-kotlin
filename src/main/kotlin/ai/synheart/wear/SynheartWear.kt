package ai.synheart.wear

import android.content.Context
import ai.synheart.wear.config.SynheartWearConfig
import ai.synheart.wear.models.*
import ai.synheart.wear.adapters.HealthConnectAdapter
import ai.synheart.wear.adapters.CloudWearableAdapter
import ai.synheart.wear.adapters.WearAdapter
import ai.synheart.wear.adapters.WhoopProvider
import ai.synheart.wear.adapters.GarminProvider
import ai.synheart.wear.adapters.WearableProvider
import ai.synheart.wear.adapters.BleHrmProvider
import ai.synheart.wear.adapters.GarminHealth
import ai.synheart.wear.cache.LocalCache
import ai.synheart.wear.consent.ConsentManager
import ai.synheart.wear.normalization.Normalizer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.serialization.InternalSerializationApi

/**
 * Main SynheartWear SDK class implementing RFC specifications
 *
 * Provides unified access to biometric data from multiple wearable devices
 * with standardized output format, encryption, and privacy controls.
 *
 * @param context Android application context
 * @param config SDK configuration
 */
@OptIn(InternalSerializationApi::class)
class SynheartWear(
    private val context: Context,
    private val config: SynheartWearConfig = SynheartWearConfig()
) {
    private var initialized = false
    private val normalizer = Normalizer()
    private val consentManager = ConsentManager(context)
    private val localCache = LocalCache(context, config.enableEncryption)

    // Wearable providers for cloud integrations
    private var whoopProvider: WhoopProvider? = null
    private var garminProvider: GarminProvider? = null

    // BLE HRM provider
    private var _bleHrmProvider: BleHrmProvider? = null

    // Garmin Health SDK provider (native device integration)
    private var _garminHealth: GarminHealth? = null

    /** BLE Heart Rate Monitor provider for direct BLE sensor access */
    val bleHrm: BleHrmProvider? get() = _bleHrmProvider

    /**
     * Garmin Health SDK provider for native device integration (scan, pair, stream)
     *
     * Available when a [GarminHealth] instance is provided via [setGarminHealth].
     * The Garmin Health SDK real-time streaming (RTS) capability requires a
     * separate license from Garmin. This facade is available on demand for
     * licensed integrations. The underlying native SDK code is proprietary
     * to Garmin and is not distributed as open source.
     */
    val garminHealth: GarminHealth? get() = _garminHealth

    /**
     * Set the Garmin Health SDK provider for native device integration
     *
     * @param garminHealth A configured GarminHealth instance with a valid Garmin SDK license key
     */
    fun setGarminHealth(garminHealth: GarminHealth) {
        _garminHealth = garminHealth
    }

    private val adapterRegistry: Map<DeviceAdapter, WearAdapter> by lazy {
        val adapters = mutableMapOf<DeviceAdapter, WearAdapter>(
            DeviceAdapter.HEALTH_CONNECT to HealthConnectAdapter(context)
        )

        // Add cloud adapters if cloud config is provided
        config.cloudConfig?.let { cloudConfig ->
            // Initialize WHOOP provider if enabled
            if (DeviceAdapter.WHOOP in config.enabledAdapters) {
                whoopProvider = WhoopProvider(context, cloudConfig)
                adapters[DeviceAdapter.WHOOP] = CloudWearableAdapter(
                    context = context,
                    vendor = DeviceAdapter.WHOOP,
                    cloudConfig = cloudConfig
                )
            }
            // Initialize Garmin provider if enabled
            if (DeviceAdapter.GARMIN in config.enabledAdapters) {
                garminProvider = GarminProvider(context, cloudConfig)
                adapters[DeviceAdapter.GARMIN] = CloudWearableAdapter(
                    context = context,
                    vendor = DeviceAdapter.GARMIN,
                    cloudConfig = cloudConfig
                )
            }
            if (DeviceAdapter.FITBIT in config.enabledAdapters) {
                adapters[DeviceAdapter.FITBIT] = CloudWearableAdapter(
                    context = context,
                    vendor = DeviceAdapter.FITBIT,
                    cloudConfig = cloudConfig
                )
            }
        }

        // Initialize BLE HRM provider if enabled
        if (DeviceAdapter.BLE_HRM in config.enabledAdapters) {
            _bleHrmProvider = BleHrmProvider(context)
        }

        adapters.toMap()
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
     * Reads metrics from all available sources (Health Connect and connected cloud providers)
     * and merges them into a unified WearMetrics object.
     *
     * @param isRealTime Whether to read real-time data or historical snapshot
     * @return Unified WearMetrics containing all available biometric data
     * @throws SynheartWearException if metrics cannot be read
     */
    @OptIn(InternalSerializationApi::class)
    suspend fun readMetrics(isRealTime: Boolean = false): WearMetrics {
        ensureInitialized()

        try {
            // Validate consents
            consentManager.validateConsents(getRequiredPermissions())

            val allMetrics = mutableListOf<WearMetrics>()

            // Gather data from enabled adapters (Health Connect, etc.)
            val adapterData = enabledAdapters().mapNotNull { adapter ->
                try {
                    adapter.readSnapshot(isRealTime)
                } catch (e: Exception) {
                    // Log but continue with other adapters
                    android.util.Log.w("SynheartWear", "Failed to read from adapter: ${e.message}")
                    null
                }
            }
            allMetrics.addAll(adapterData)

            // Read from WHOOP if connected
            if (config.enabledAdapters.contains(DeviceAdapter.WHOOP) && 
                whoopProvider?.isConnected() == true) {
                try {
                    // Fetch latest recovery data (most recent record)
                    val yesterday = java.util.Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
                    val now = java.util.Date()
                    val recoveryData = whoopProvider!!.fetchRecovery(
                        startDate = yesterday,
                        endDate = now,
                        limit = 1
                    )
                    
                    if (recoveryData.isNotEmpty()) {
                        allMetrics.add(recoveryData.first())
                    }
                } catch (e: Exception) {
                    // Log but don't fail - continue with other sources
                    android.util.Log.w("SynheartWear", "Failed to read WHOOP metrics: ${e.message}")
                }
            }

            // Include BLE HRM last sample if connected
            _bleHrmProvider?.let { bleProvider ->
                if (bleProvider.isConnected()) {
                    bleProvider.lastSample?.let { sample ->
                        allMetrics.add(sample.toWearMetrics())
                    }
                }
            }

            // Merge all metrics from different sources
            val mergedData = if (allMetrics.isEmpty()) {
                // No data available from any source
                WearMetrics.builder()
                    .timestamp(System.currentTimeMillis())
                    .deviceId("unknown")
                    .source("none")
                    .metaData("error", "No data sources available")
                    .build()
            } else if (allMetrics.size == 1) {
                // Only one source available
                allMetrics.first()
            } else {
                // Multiple sources - merge them
                normalizer.mergeSnapshots(allMetrics)
            }

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

    /**
     * Get cloud wearable adapter for a specific vendor
     *
     * @param vendor Device adapter type (WHOOP, GARMIN, FITBIT)
     * @return CloudWearableAdapter instance or null if not enabled
     */
    fun getCloudAdapter(vendor: DeviceAdapter): CloudWearableAdapter? {
        ensureInitialized()
        return adapterRegistry[vendor] as? CloudWearableAdapter
    }

    /**
     * Get wearable provider for a specific vendor
     *
     * Provides access to vendor-specific provider with dedicated methods
     * for that vendor (e.g., WhoopProvider for WHOOP)
     *
     * @param vendor Device adapter type (e.g., WHOOP)
     * @return WearableProvider instance or null if not enabled/configured
     * @throws SynheartWearException if provider is not available
     */
    fun getProvider(vendor: DeviceAdapter): WearableProvider {
        ensureInitialized()
        
        return when (vendor) {
            DeviceAdapter.WHOOP -> whoopProvider
                ?: throw SynheartWearException("WHOOP provider not configured. Please provide cloudConfig in SynheartWearConfig.")
            DeviceAdapter.GARMIN -> garminProvider
                ?: throw SynheartWearException("Garmin provider not configured. Please provide cloudConfig in SynheartWearConfig.")
            DeviceAdapter.FITBIT -> 
                throw SynheartWearException("Provider for $vendor not yet implemented.")
            else -> 
                throw SynheartWearException("Provider for $vendor not available.")
        }
    }

    /**
     * Check if a cloud wearable is enabled and configured
     *
     * @param vendor Device adapter type (WHOOP, GARMIN, FITBIT)
     * @return True if cloud adapter is enabled and configured
     */
    fun isCloudAdapterEnabled(vendor: DeviceAdapter): Boolean {
        return vendor in config.enabledAdapters && config.cloudConfig != null
    }
    
    /**
     * Read metrics from a specific provider without merging
     *
     * Useful for provider-specific data or historical queries
     *
     * @param vendor Device adapter type (e.g., WHOOP)
     * @param startDate Start date for data query (optional)
     * @param endDate End date for data query (optional)
     * @param limit Maximum number of records (optional)
     * @return List of WearMetrics from the specified provider
     */
    suspend fun readMetricsFromProvider(
        vendor: DeviceAdapter,
        startDate: java.util.Date? = null,
        endDate: java.util.Date? = null,
        limit: Int? = null
    ): List<WearMetrics> {
        ensureInitialized()
        
        return when (vendor) {
            DeviceAdapter.WHOOP -> {
                val provider = whoopProvider
                    ?: throw SynheartWearException("WHOOP provider not configured")
                if (!provider.isConnected()) {
                    throw SynheartWearException("Not connected to WHOOP. Call getProvider(WHOOP).connect() first.")
                }
                provider.fetchRecovery(startDate, endDate, limit)
            }
            DeviceAdapter.HEALTH_CONNECT -> {
                // For Health Connect, return current metrics
                listOf(readMetrics())
            }
            else -> throw SynheartWearException("Provider for $vendor not yet implemented.")
        }
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
 *
 * @property code Error code for programmatic handling
 */
class SynheartWearException(
    message: String,
    cause: Throwable? = null,
    val code: String? = null
) : Exception(message, cause)
