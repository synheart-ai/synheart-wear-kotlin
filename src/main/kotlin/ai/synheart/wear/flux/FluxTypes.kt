package ai.synheart.wear.flux

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * HSI 1.0 Compliant Types for Synheart Flux
 *
 * These types follow the Health Signal Interface (HSI) 1.0 specification
 * for representing biometric and behavioral data.
 */

// --------------------------------------------------------------------------
// Vendor Types
// --------------------------------------------------------------------------

/**
 * Supported wearable vendors for Flux processing
 */
enum class Vendor(val value: String) {
    WHOOP("whoop"),
    GARMIN("garmin")
}

// --------------------------------------------------------------------------
// HSI 1.0 Core Types
// --------------------------------------------------------------------------

/**
 * HSI 1.0 compliant payload
 *
 * This is the top-level structure for HSI output containing all metadata,
 * windows, sources, axes, and privacy information.
 */
@Serializable
data class HsiPayload(
    @SerialName("hsi_version") val hsiVersion: String = "1.0",
    @SerialName("observed_at_utc") val observedAtUtc: String,
    @SerialName("computed_at_utc") val computedAtUtc: String,
    val producer: HsiProducer,
    @SerialName("window_ids") val windowIds: List<String>,
    val windows: Map<String, HsiWindow>,
    @SerialName("source_ids") val sourceIds: List<String>,
    val sources: Map<String, HsiSource>,
    val axes: HsiAxes,
    val privacy: HsiPrivacy,
    val meta: Map<String, JsonElement>? = null
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Parse an HsiPayload from JSON string
         */
        fun fromJson(jsonString: String): HsiPayload {
            return json.decodeFromString(serializer(), jsonString)
        }
    }

    /**
     * Convert to JSON string
     */
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}

/**
 * HSI producer metadata
 */
@Serializable
data class HsiProducer(
    val name: String,
    val version: String,
    @SerialName("instance_id") val instanceId: String
)

/**
 * HSI time window
 */
@Serializable
data class HsiWindow(
    val start: String,
    val end: String,
    val label: String? = null
)

/**
 * HSI source type
 */
@Serializable
enum class HsiSourceType {
    @SerialName("sensor") SENSOR,
    @SerialName("app") APP,
    @SerialName("self_report") SELF_REPORT,
    @SerialName("observer") OBSERVER,
    @SerialName("derived") DERIVED,
    @SerialName("other") OTHER
}

/**
 * HSI data source
 */
@Serializable
data class HsiSource(
    val type: HsiSourceType,
    val quality: Double,
    val degraded: Boolean
)

/**
 * HSI direction indicator
 */
@Serializable
enum class HsiDirection {
    @SerialName("higher_is_more") HIGHER_IS_MORE,
    @SerialName("higher_is_less") HIGHER_IS_LESS,
    @SerialName("bidirectional") BIDIRECTIONAL
}

/**
 * HSI axis reading
 */
@Serializable
data class HsiAxisReading(
    val axis: String,
    val score: Double,
    val confidence: Double,
    @SerialName("window_id") val windowId: String,
    val direction: HsiDirection,
    val unit: String? = null,
    @SerialName("evidence_source_ids") val evidenceSourceIds: List<String>? = null,
    val notes: String? = null
)

/**
 * HSI axes domain (contains readings for a category)
 */
@Serializable
data class HsiAxesDomain(
    val readings: List<HsiAxisReading> = emptyList()
)

/**
 * HSI axes (all domains)
 */
@Serializable
data class HsiAxes(
    val affect: HsiAxesDomain? = null,
    val engagement: HsiAxesDomain? = null,
    val behavior: HsiAxesDomain? = null
)

/**
 * HSI privacy settings
 */
@Serializable
data class HsiPrivacy(
    @SerialName("contains_pii") val containsPii: Boolean = false,
    @SerialName("raw_biosignals_allowed") val rawBiosignalsAllowed: Boolean = false,
    @SerialName("derived_metrics_allowed") val derivedMetricsAllowed: Boolean = true,
    val purposes: List<String>? = null,
    val notes: String? = null
)

// --------------------------------------------------------------------------
// Baselines Types
// --------------------------------------------------------------------------

/**
 * Flux baseline state
 *
 * Contains rolling baseline values computed from historical data
 */
@Serializable
data class Baselines(
    @SerialName("hrv_baseline_ms") val hrvBaselineMs: Double? = null,
    @SerialName("rhr_baseline_bpm") val rhrBaselineBpm: Int? = null,
    @SerialName("sleep_baseline_minutes") val sleepBaselineMinutes: Int? = null,
    @SerialName("sleep_efficiency_baseline") val sleepEfficiencyBaseline: Double? = null,
    @SerialName("baseline_days") val baselineDays: Int = 0
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Parse Baselines from JSON string
         */
        fun fromJson(jsonString: String): Baselines {
            return json.decodeFromString(serializer(), jsonString)
        }
    }

    /**
     * Convert to JSON string
     */
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}

// --------------------------------------------------------------------------
// Legacy Types (for backwards compatibility)
// --------------------------------------------------------------------------

/**
 * Legacy HSI provenance information
 * @deprecated Use HsiPayload with observedAtUtc/computedAtUtc for HSI 1.0 output
 */
@Deprecated("Use HsiPayload for HSI 1.0 output")
@Serializable
data class LegacyHsiProvenance(
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("observed_at_utc") val observedAtUtc: String,
    @SerialName("computed_at_utc") val computedAtUtc: String
)

/**
 * Legacy HSI quality indicator
 * @deprecated Use HsiSource.quality for HSI 1.0 output
 */
@Deprecated("Use HsiSource for HSI 1.0 output")
@Serializable
data class LegacyHsiQuality(
    val coverage: Double,
    val confidence: Double,
    val flags: List<String> = emptyList()
)
