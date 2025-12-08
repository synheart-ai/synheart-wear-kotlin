package ai.synheart.wear.adapters

import android.content.Context
import ai.synheart.wear.SynheartWearException
import ai.synheart.wear.cloud.WearServiceAPI
import ai.synheart.wear.cloud.models.*
import ai.synheart.wear.config.CloudConfig
import ai.synheart.wear.models.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Cloud wearable adapter for vendor integrations (WHOOP, Garmin, Fitbit)
 *
 * Communicates with the Synheart Wear Service backend to fetch
 * data from cloud-based wearable providers.
 *
 * @param context Android application context
 * @param vendor Vendor type (WHOOP, GARMIN, FITBIT)
 * @param cloudConfig Cloud configuration with base URL and app ID
 */
@OptIn(InternalSerializationApi::class)
class CloudWearableAdapter(
    private val context: Context,
    private val vendor: DeviceAdapter,
    private val cloudConfig: CloudConfig,
    apiOverride: WearServiceAPI? = null
) : WearAdapter {

    override val id: String = "cloud_${vendor.name.lowercase()}"

    private val api: WearServiceAPI = apiOverride ?: createRetrofitClient()

    private var userId: String? = null
    private var isInitialized = false
    private var isConnected = false

    override suspend fun initialize() {
        isInitialized = true
        // Load userId from shared preferences if previously connected
        userId = loadUserId()
        isConnected = userId != null
    }

    override suspend fun requestPermissions(permissions: Set<PermissionType>): Map<PermissionType, Boolean> {
        // Cloud wearables use OAuth instead of Android permissions
        // Return all as granted if connected, all denied if not
        return permissions.associateWith { isConnected }
    }

    override fun getPermissionStatus(): Map<PermissionType, Boolean> {
        // Return connected status for all permissions
        return PermissionType.values().associateWith { isConnected }
    }

    override suspend fun readSnapshot(isRealTime: Boolean): WearMetrics {
        ensureInitialized()
        ensureConnected()

        return withContext(Dispatchers.IO) {
            try {
                // Fetch latest recovery data from vendor
                val recoveryResponse = api.getRecoveryData(
                    vendor = vendor.name.lowercase(),
                    userId = userId!!,
                    appId = cloudConfig.appId,
                    limit = 1
                )

                if (!recoveryResponse.isSuccessful) {
                    throw SynheartWearException(
                        "Failed to fetch recovery data: ${recoveryResponse.code()}"
                    )
                }

                val envelope = recoveryResponse.body()
                    ?: throw SynheartWearException("Empty response from wear service")

                // Convert cloud data to WearMetrics
                convertToWearMetrics(envelope.records.firstOrNull())
            } catch (e: Exception) {
                throw SynheartWearException(
                    "Failed to read cloud wearable snapshot: ${e.message}",
                    e
                )
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        // Check if wear service is reachable
        return try {
            val response = api.getIntegration(
                vendor = vendor.name.lowercase(),
                appId = cloudConfig.appId
            )
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // ============= Cloud-Specific Methods =============

    /**
     * Start OAuth connection flow
     *
     * Returns authorization URL that should be opened in browser
     * Uses redirectUri from CloudConfig by default
     *
     * @param redirectUri OAuth redirect URI for callback (defaults to config value)
     * @param state OAuth state parameter for CSRF protection
     * @return Authorization URL to open in browser
     */
    suspend fun startOAuthFlow(
        redirectUri: String = cloudConfig.redirectUri,
        state: String = UUID.randomUUID().toString()
    ): String {
        ensureInitialized()

        return withContext(Dispatchers.IO) {
            try {
                val response = api.getAuthorizationUrl(
                    vendor = vendor.name.lowercase(),
                    redirectUri = redirectUri,
                    state = state,
                    appId = cloudConfig.appId
                )

                if (!response.isSuccessful) {
                    throw SynheartWearException(
                        "Failed to get authorization URL: ${response.code()}"
                    )
                }

                // Save state for validation
                saveOAuthState(state)

                response.body()?.authorizationUrl
                    ?: throw SynheartWearException("Empty authorization URL")
            } catch (e: Exception) {
                throw SynheartWearException(
                    "Failed to start OAuth flow: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Complete OAuth connection with authorization code
     *
     * @param code Authorization code from OAuth callback
     * @param state OAuth state parameter (must match saved state)
     * @param redirectUri OAuth redirect URI (defaults to config value, must match start flow URI)
     * @return User ID from successful connection
     */
    suspend fun completeOAuthFlow(
        code: String,
        state: String,
        redirectUri: String = cloudConfig.redirectUri
    ): String {
        ensureInitialized()

        return withContext(Dispatchers.IO) {
            try {
                // Validate state
                val savedState = loadOAuthState()
                if (state != savedState) {
                    throw SynheartWearException("Invalid OAuth state parameter")
                }

                val request = OAuthCallbackRequest(
                    code = code,
                    state = state,
                    redirectUri = redirectUri
                )

                val response = api.handleOAuthCallback(
                    vendor = vendor.name.lowercase(),
                    request = request
                )

                if (!response.isSuccessful) {
                    throw SynheartWearException(
                        "Failed to complete OAuth: ${response.code()}"
                    )
                }

                val callbackResponse = response.body()
                    ?: throw SynheartWearException("Empty callback response")

                // Save user ID and mark as connected
                userId = callbackResponse.userId
                isConnected = true
                saveUserId(callbackResponse.userId)
                clearOAuthState()

                callbackResponse.userId
            } catch (e: Exception) {
                throw SynheartWearException(
                    "Failed to complete OAuth flow: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Disconnect from cloud wearable
     */
    suspend fun disconnect() {
        ensureInitialized()
        ensureConnected()

        withContext(Dispatchers.IO) {
            try {
                val response = api.disconnect(
                    vendor = vendor.name.lowercase(),
                    userId = userId!!,
                    appId = cloudConfig.appId
                )

                if (response.isSuccessful) {
                    // Clear local connection state
                    userId = null
                    isConnected = false
                    clearUserId()
                }
            } catch (e: Exception) {
                throw SynheartWearException(
                    "Failed to disconnect: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Fetch recovery data from cloud wearable
     *
     * @param startDate Start date for data query
     * @param endDate End date for data query
     * @param limit Maximum number of records
     * @return List of recovery records
     */
    suspend fun fetchRecoveryData(
        startDate: Date? = null,
        endDate: Date? = null,
        limit: Int = 100
    ): List<Map<String, Any>> {
        ensureInitialized()
        ensureConnected()

        return withContext(Dispatchers.IO) {
            try {
                val response = api.getRecoveryData(
                    vendor = vendor.name.lowercase(),
                    userId = userId!!,
                    appId = cloudConfig.appId,
                    start = startDate?.toRFC3339(),
                    end = endDate?.toRFC3339(),
                    limit = limit
                )

                if (!response.isSuccessful) {
                    throw SynheartWearException(
                        "Failed to fetch recovery data: ${response.code()}"
                    )
                }

                response.body()?.records ?: emptyList()
            } catch (e: Exception) {
                throw SynheartWearException(
                    "Failed to fetch recovery data: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Fetch sleep data from cloud wearable
     *
     * @param startDate Start date for data query
     * @param endDate End date for data query
     * @param limit Maximum number of records
     * @return List of sleep records
     */
    suspend fun fetchSleepData(
        startDate: Date? = null,
        endDate: Date? = null,
        limit: Int = 100
    ): List<Map<String, Any>> {
        ensureInitialized()
        ensureConnected()

        return withContext(Dispatchers.IO) {
            try {
                val response = api.getSleepData(
                    vendor = vendor.name.lowercase(),
                    userId = userId!!,
                    appId = cloudConfig.appId,
                    start = startDate?.toRFC3339(),
                    end = endDate?.toRFC3339(),
                    limit = limit
                )

                if (!response.isSuccessful) {
                    throw SynheartWearException(
                        "Failed to fetch sleep data: ${response.code()}"
                    )
                }

                response.body()?.records ?: emptyList()
            } catch (e: Exception) {
                throw SynheartWearException(
                    "Failed to fetch sleep data: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Check if user is connected to this cloud wearable
     */
    fun isConnectedToCloud(): Boolean = isConnected

    /**
     * Get the current user ID (if connected)
     */
    fun getUserId(): String? = userId

    // ============= Private Helper Methods =============

    private fun createRetrofitClient(): WearServiceAPI {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (cloudConfig.enableDebugLogging) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(cloudConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(WearServiceAPI::class.java)
    }

    private fun convertToWearMetrics(record: Map<String, Any>?): WearMetrics {
        if (record == null) {
            return WearMetrics.builder()
                .timestamp(System.currentTimeMillis())
                .deviceId(id)
                .source(vendor.name.lowercase())
                .build()
        }

        val builder = WearMetrics.builder()
            .timestamp(System.currentTimeMillis())
            .deviceId(id)
            .source(vendor.name.lowercase())

        // Extract metrics from vendor-specific record
        // This is a simplified conversion - customize per vendor
        record["score"]?.let { builder.metaData("score", it.toString()) }
        record["heart_rate"]?.let {
            builder.metric(MetricType.HR, (it as? Number)?.toDouble() ?: 0.0)
        }
        record["hrv"]?.let {
            builder.metric(MetricType.HRV_RMSSD, (it as? Number)?.toDouble() ?: 0.0)
        }

        return builder.build()
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw SynheartWearException("CloudWearableAdapter not initialized")
        }
    }

    private fun ensureConnected() {
        if (!isConnected || userId == null) {
            throw SynheartWearException(
                "Not connected to ${vendor.name}. Call startOAuthFlow() first."
            )
        }
    }

    private fun getSharedPrefs() = context.getSharedPreferences(
        "synheart_cloud_${vendor.name.lowercase()}",
        Context.MODE_PRIVATE
    )

    private fun saveUserId(userId: String) {
        getSharedPrefs().edit()
            .putString("user_id", userId)
            .apply()
    }

    private fun loadUserId(): String? {
        return getSharedPrefs().getString("user_id", null)
    }

    private fun clearUserId() {
        getSharedPrefs().edit()
            .remove("user_id")
            .apply()
    }

    private fun saveOAuthState(state: String) {
        getSharedPrefs().edit()
            .putString("oauth_state", state)
            .apply()
    }

    private fun loadOAuthState(): String? {
        return getSharedPrefs().getString("oauth_state", null)
    }

    private fun clearOAuthState() {
        getSharedPrefs().edit()
            .remove("oauth_state")
            .apply()
    }

    private fun Date.toRFC3339(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(this)
    }
}

