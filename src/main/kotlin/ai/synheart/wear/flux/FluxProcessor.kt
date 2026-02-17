package ai.synheart.wear.flux

import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Stateful processor for Synheart Flux with persistent baselines
 *
 * Use this when you need to maintain baselines across multiple API calls.
 * This class wraps the native Rust FluxProcessor via JNI.
 *
 * Flux is optional - if the native library is not available:
 * - `isAvailable` returns false
 * - Processing methods return null
 * - Baseline methods return null/false
 *
 * Example:
 * ```kotlin
 * val processor = FluxProcessor()
 *
 * if (!processor.isAvailable) {
 *     Log.w("App", "Flux not available, using fallback")
 *     return
 * }
 *
 * // Process WHOOP data
 * val results = processor.processWhoop(whoopJson, "America/New_York", "device-123")
 *
 * // Save baselines for later
 * val savedBaselines = processor.saveBaselines()
 *
 * // ... later ...
 *
 * // Load baselines and continue processing
 * processor.loadBaselines(savedBaselines ?: "{}")
 * val moreResults = processor.processWhoop(moreWhoopJson, "America/New_York", "device-123")
 *
 * // Don't forget to close when done
 * processor.close()
 * ```
 */
class FluxProcessor private constructor(
    private var handle: Long
) : AutoCloseable {

    companion object {
        private const val TAG = "FluxProcessor"
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Check if Flux native library is available (static check)
         */
        val isFluxAvailable: Boolean
            get() = FluxFfi.isAvailable

        /**
         * Get the Flux load error if any
         */
        val fluxLoadError: String?
            get() = FluxFfi.getLoadError()

        /**
         * Create a new FluxProcessor with default settings (14-day baseline window)
         *
         * If Flux native library is not available, `isAvailable` will return false
         * and all processing methods will return null (graceful degradation).
         */
        fun create(): FluxProcessor = create(14)

        /**
         * Create a FluxProcessor with a specific baseline window size
         *
         * If Flux native library is not available, `isAvailable` will return false
         * and all processing methods will return null (graceful degradation).
         *
         * @param baselineWindowDays Number of days for the baseline window
         */
        fun create(baselineWindowDays: Int): FluxProcessor {
            val handle = FluxFfi.processorNew(baselineWindowDays)
            if (handle == 0L) {
                Log.w(TAG, "Native library not available, running in degraded mode")
            }
            return FluxProcessor(handle)
        }
    }

    /**
     * Check if this processor is available (native library loaded and not closed)
     */
    val isAvailable: Boolean
        get() = handle != 0L

    /**
     * Load baseline state from JSON
     *
     * @param jsonString Baselines JSON string (from saveBaselines)
     * @return true if successful, false if failed or Flux unavailable
     */
    fun loadBaselines(jsonString: String): Boolean {
        if (handle == 0L) {
            Log.d(TAG, "Cannot load baselines - not available")
            return false
        }
        return FluxFfi.processorLoadBaselines(handle, jsonString)
    }

    /**
     * Save baseline state to JSON
     *
     * @return Baselines JSON string or null if Flux is not available
     */
    fun saveBaselines(): String? {
        if (handle == 0L) {
            Log.d(TAG, "Cannot save baselines - not available")
            return null
        }
        return FluxFfi.processorSaveBaselines(handle)
    }

    /**
     * Get current baselines as typed object
     *
     * @return Baselines object or null if Flux is not available
     */
    val currentBaselines: Baselines?
        get() {
            val jsonString = saveBaselines() ?: return null
            return try {
                Baselines.fromJson(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse baselines", e)
                null
            }
        }

    /**
     * Process WHOOP payload with persistent baselines
     *
     * @param rawJson Raw WHOOP API response JSON
     * @param timezone User's timezone (e.g., "America/New_York")
     * @param deviceId Unique device identifier
     * @return List of HSI JSON payloads, or null if Flux is not available
     */
    fun processWhoop(
        rawJson: String,
        timezone: String,
        deviceId: String
    ): List<String>? {
        if (handle == 0L) {
            Log.d(TAG, "Cannot process WHOOP - not available")
            return null
        }
        val resultJson = FluxFfi.processorProcessWhoop(handle, rawJson, timezone, deviceId)
            ?: return null
        return parseJsonArray(resultJson)
    }

    /**
     * Process Garmin payload with persistent baselines
     *
     * @param rawJson Raw Garmin API response JSON
     * @param timezone User's timezone (e.g., "America/Los_Angeles")
     * @param deviceId Unique device identifier
     * @return List of HSI JSON payloads, or null if Flux is not available
     */
    fun processGarmin(
        rawJson: String,
        timezone: String,
        deviceId: String
    ): List<String>? {
        if (handle == 0L) {
            Log.d(TAG, "Cannot process Garmin - not available")
            return null
        }
        val resultJson = FluxFfi.processorProcessGarmin(handle, rawJson, timezone, deviceId)
            ?: return null
        return parseJsonArray(resultJson)
    }

    /**
     * Close and release native resources
     *
     * After calling close, this processor can no longer be used.
     */
    override fun close() {
        if (handle != 0L) {
            FluxFfi.processorFree(handle)
            handle = 0L
        }
    }

    /**
     * Parse a JSON array string into a list of JSON strings
     */
    private fun parseJsonArray(jsonArrayStr: String): List<String> {
        return try {
            val decoded = json.parseToJsonElement(jsonArrayStr)
            if (decoded is kotlinx.serialization.json.JsonArray) {
                decoded.map { it.toString() }
            } else {
                // Single object, wrap in list
                listOf(jsonArrayStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON array", e)
            emptyList()
        }
    }
}

// --------------------------------------------------------------------------
// Stateless Functions (one-shot processing)
// --------------------------------------------------------------------------

/**
 * Convert raw WHOOP JSON payload to HSI 1.0 compliant daily payloads
 *
 * @param rawJson Raw WHOOP API response JSON
 * @param timezone User's timezone (e.g., "America/New_York")
 * @param deviceId Unique device identifier
 * @return List of HSI JSON payloads (one per day in the input),
 *         or null if Flux is not available (graceful degradation)
 *
 * Example:
 * ```kotlin
 * val hsiPayloads = whoopToHsiDaily(whoopJson, "America/New_York", "device-123")
 * if (hsiPayloads == null) {
 *     Log.w("App", "Flux not available")
 * }
 * ```
 */
fun whoopToHsiDaily(
    rawJson: String,
    timezone: String,
    deviceId: String
): List<String>? {
    val resultJson = FluxFfi.whoopToHsiDaily(rawJson, timezone, deviceId)
        ?: return null
    return parseJsonArrayStatic(resultJson)
}

/**
 * Convert raw Garmin JSON payload to HSI 1.0 compliant daily payloads
 *
 * @param rawJson Raw Garmin API response JSON
 * @param timezone User's timezone (e.g., "America/Los_Angeles")
 * @param deviceId Unique device identifier
 * @return List of HSI JSON payloads (one per day in the input),
 *         or null if Flux is not available (graceful degradation)
 *
 * Example:
 * ```kotlin
 * val hsiPayloads = garminToHsiDaily(garminJson, "America/Los_Angeles", "garmin-device-456")
 * if (hsiPayloads == null) {
 *     Log.w("App", "Flux not available")
 * }
 * ```
 */
fun garminToHsiDaily(
    rawJson: String,
    timezone: String,
    deviceId: String
): List<String>? {
    val resultJson = FluxFfi.garminToHsiDaily(rawJson, timezone, deviceId)
        ?: return null
    return parseJsonArrayStatic(resultJson)
}

/**
 * Check if Flux native library is available
 */
val isFluxAvailable: Boolean
    get() = FluxFfi.isAvailable

/**
 * Get the Flux load error if any
 */
val fluxLoadError: String?
    get() = FluxFfi.getLoadError()

/**
 * Parse a JSON array string into a list of JSON strings
 */
private fun parseJsonArrayStatic(jsonArrayStr: String): List<String> {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    return try {
        val decoded = json.parseToJsonElement(jsonArrayStr)
        if (decoded is kotlinx.serialization.json.JsonArray) {
            decoded.map { it.toString() }
        } else {
            // Single object, wrap in list
            listOf(jsonArrayStr)
        }
    } catch (e: Exception) {
        Log.e("FluxProcessor", "Failed to parse JSON array", e)
        emptyList()
    }
}
