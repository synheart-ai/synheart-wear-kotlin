package ai.synheart.wear.flux

import android.util.Log

/**
 * JNI bindings for Synheart Flux native library
 *
 * This class provides access to the Rust Flux library via JNI.
 * If the native library is not available, all methods return null
 * (graceful degradation).
 */
object FluxFfi {
    private const val TAG = "FluxFfi"
    private const val LIB_NAME = "synheart_flux"

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loadError: String? = null

    @Volatile
    private var isLoaded = false

    /**
     * Check if the native Flux library is available
     */
    val isAvailable: Boolean
        get() {
            ensureLoadAttempted()
            return isLoaded
        }

    /**
     * Get the error message if the library failed to load
     */
    fun getLoadError(): String? {
        ensureLoadAttempted()
        return loadError
    }

    /**
     * Reset the load state for testing purposes
     */
    @Synchronized
    fun resetForTesting() {
        loadAttempted = false
        loadError = null
        isLoaded = false
    }

    @Synchronized
    private fun ensureLoadAttempted() {
        if (loadAttempted) return
        loadAttempted = true

        try {
            System.loadLibrary(LIB_NAME)
            isLoaded = true
            Log.i(TAG, "Successfully loaded native library: $LIB_NAME")
        } catch (e: UnsatisfiedLinkError) {
            loadError = "Failed to load native library: ${e.message}"
            Log.w(TAG, loadError!!)
        } catch (e: Exception) {
            loadError = "Unexpected error loading native library: ${e.message}"
            Log.e(TAG, loadError!!, e)
        }
    }

    // --------------------------------------------------------------------------
    // Native method declarations (JNI)
    // --------------------------------------------------------------------------

    /**
     * Process WHOOP JSON and return HSI JSON (stateless)
     *
     * @param json Raw WHOOP API response JSON
     * @param timezone User's timezone (e.g., "America/New_York")
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null on failure
     */
    @JvmStatic
    private external fun nativeWhoopToHsiDaily(
        json: String,
        timezone: String,
        deviceId: String
    ): String?

    /**
     * Process Garmin JSON and return HSI JSON (stateless)
     *
     * @param json Raw Garmin API response JSON
     * @param timezone User's timezone (e.g., "America/Los_Angeles")
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null on failure
     */
    @JvmStatic
    private external fun nativeGarminToHsiDaily(
        json: String,
        timezone: String,
        deviceId: String
    ): String?

    /**
     * Create a new FluxProcessor instance
     *
     * @param baselineWindowDays Number of days for baseline window
     * @return Native pointer handle or 0 on failure
     */
    @JvmStatic
    private external fun nativeProcessorNew(baselineWindowDays: Int): Long

    /**
     * Free a FluxProcessor instance
     *
     * @param handle Native pointer handle
     */
    @JvmStatic
    private external fun nativeProcessorFree(handle: Long)

    /**
     * Process WHOOP data with stateful processor
     *
     * @param handle Native pointer handle
     * @param json Raw WHOOP API response JSON
     * @param timezone User's timezone
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null on failure
     */
    @JvmStatic
    private external fun nativeProcessorProcessWhoop(
        handle: Long,
        json: String,
        timezone: String,
        deviceId: String
    ): String?

    /**
     * Process Garmin data with stateful processor
     *
     * @param handle Native pointer handle
     * @param json Raw Garmin API response JSON
     * @param timezone User's timezone
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null on failure
     */
    @JvmStatic
    private external fun nativeProcessorProcessGarmin(
        handle: Long,
        json: String,
        timezone: String,
        deviceId: String
    ): String?

    /**
     * Save processor baselines to JSON
     *
     * @param handle Native pointer handle
     * @return Baselines JSON string or null on failure
     */
    @JvmStatic
    private external fun nativeProcessorSaveBaselines(handle: Long): String?

    /**
     * Load processor baselines from JSON
     *
     * @param handle Native pointer handle
     * @param json Baselines JSON string
     * @return 0 on success, non-zero on failure
     */
    @JvmStatic
    private external fun nativeProcessorLoadBaselines(handle: Long, json: String): Int

    /**
     * Get the last error message
     *
     * @return Error message or null if no error
     */
    @JvmStatic
    private external fun nativeLastError(): String?

    // --------------------------------------------------------------------------
    // Public API (with graceful degradation)
    // --------------------------------------------------------------------------

    /**
     * Process WHOOP JSON and return HSI JSON (stateless)
     *
     * @param json Raw WHOOP API response JSON
     * @param timezone User's timezone (e.g., "America/New_York")
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null if Flux is not available
     */
    fun whoopToHsiDaily(json: String, timezone: String, deviceId: String): String? {
        if (!isAvailable) {
            Log.d(TAG, "whoopToHsiDaily: Flux not available")
            return null
        }
        return try {
            nativeWhoopToHsiDaily(json, timezone, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "whoopToHsiDaily failed", e)
            null
        }
    }

    /**
     * Process Garmin JSON and return HSI JSON (stateless)
     *
     * @param json Raw Garmin API response JSON
     * @param timezone User's timezone (e.g., "America/Los_Angeles")
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null if Flux is not available
     */
    fun garminToHsiDaily(json: String, timezone: String, deviceId: String): String? {
        if (!isAvailable) {
            Log.d(TAG, "garminToHsiDaily: Flux not available")
            return null
        }
        return try {
            nativeGarminToHsiDaily(json, timezone, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "garminToHsiDaily failed", e)
            null
        }
    }

    /**
     * Create a new FluxProcessor instance
     *
     * @param baselineWindowDays Number of days for baseline window (default: 14)
     * @return Native pointer handle or 0 if Flux is not available
     */
    internal fun processorNew(baselineWindowDays: Int = 14): Long {
        if (!isAvailable) {
            Log.d(TAG, "processorNew: Flux not available")
            return 0L
        }
        return try {
            nativeProcessorNew(baselineWindowDays)
        } catch (e: Exception) {
            Log.e(TAG, "processorNew failed", e)
            0L
        }
    }

    /**
     * Free a FluxProcessor instance
     *
     * @param handle Native pointer handle
     */
    internal fun processorFree(handle: Long) {
        if (!isAvailable || handle == 0L) return
        try {
            nativeProcessorFree(handle)
        } catch (e: Exception) {
            Log.e(TAG, "processorFree failed", e)
        }
    }

    /**
     * Process WHOOP data with stateful processor
     *
     * @param handle Native pointer handle
     * @param json Raw WHOOP API response JSON
     * @param timezone User's timezone
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null on failure
     */
    internal fun processorProcessWhoop(
        handle: Long,
        json: String,
        timezone: String,
        deviceId: String
    ): String? {
        if (!isAvailable || handle == 0L) {
            Log.d(TAG, "processorProcessWhoop: Flux not available or invalid handle")
            return null
        }
        return try {
            nativeProcessorProcessWhoop(handle, json, timezone, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "processorProcessWhoop failed", e)
            null
        }
    }

    /**
     * Process Garmin data with stateful processor
     *
     * @param handle Native pointer handle
     * @param json Raw Garmin API response JSON
     * @param timezone User's timezone
     * @param deviceId Unique device identifier
     * @return HSI JSON string or null on failure
     */
    internal fun processorProcessGarmin(
        handle: Long,
        json: String,
        timezone: String,
        deviceId: String
    ): String? {
        if (!isAvailable || handle == 0L) {
            Log.d(TAG, "processorProcessGarmin: Flux not available or invalid handle")
            return null
        }
        return try {
            nativeProcessorProcessGarmin(handle, json, timezone, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "processorProcessGarmin failed", e)
            null
        }
    }

    /**
     * Save processor baselines to JSON
     *
     * @param handle Native pointer handle
     * @return Baselines JSON string or null on failure
     */
    internal fun processorSaveBaselines(handle: Long): String? {
        if (!isAvailable || handle == 0L) {
            Log.d(TAG, "processorSaveBaselines: Flux not available or invalid handle")
            return null
        }
        return try {
            nativeProcessorSaveBaselines(handle)
        } catch (e: Exception) {
            Log.e(TAG, "processorSaveBaselines failed", e)
            null
        }
    }

    /**
     * Load processor baselines from JSON
     *
     * @param handle Native pointer handle
     * @param json Baselines JSON string
     * @return true on success, false on failure
     */
    internal fun processorLoadBaselines(handle: Long, json: String): Boolean {
        if (!isAvailable || handle == 0L) {
            Log.d(TAG, "processorLoadBaselines: Flux not available or invalid handle")
            return false
        }
        return try {
            nativeProcessorLoadBaselines(handle, json) == 0
        } catch (e: Exception) {
            Log.e(TAG, "processorLoadBaselines failed", e)
            false
        }
    }

    /**
     * Get the last error message from the native library
     *
     * @return Error message or null if no error
     */
    fun getLastError(): String? {
        if (!isAvailable) return loadError
        return try {
            nativeLastError()
        } catch (e: Exception) {
            Log.e(TAG, "getLastError failed", e)
            null
        }
    }
}
