package ai.synheart.wear.adapters

import ai.synheart.wear.SynheartWearException
import ai.synheart.wear.cloud.WearServiceAPI
import ai.synheart.wear.cloud.models.*
import ai.synheart.wear.config.CloudConfig
import ai.synheart.wear.models.DeviceAdapter
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.Mockito.`when` as mockWhen
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import retrofit2.Response

class GarminProviderTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPrefs: SharedPreferences

    @Mock
    private lateinit var mockSharedPrefsEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockApi: WearServiceAPI

    private lateinit var cloudConfig: CloudConfig
    private lateinit var provider: GarminProvider

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Setup SharedPreferences mock
        mockWhen(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs)
        mockWhen(mockSharedPrefs.edit()).thenReturn(mockSharedPrefsEditor)
        mockWhen(mockSharedPrefsEditor.putString(anyString(), anyString())).thenReturn(mockSharedPrefsEditor)
        mockWhen(mockSharedPrefsEditor.remove(anyString())).thenReturn(mockSharedPrefsEditor)

        cloudConfig = CloudConfig(
            appId = "test-app",
            baseUrl = "https://api.wear.synheart.ai/",
            redirectUri = "synheart://oauth/callback"
        )

        provider = GarminProvider(mockContext, cloudConfig, mockApi)
    }

    @Test
    fun `vendor should be GARMIN`() {
        assertEquals(DeviceAdapter.GARMIN, provider.vendor)
    }

    @Test
    fun `isConnected should return false when no user ID stored`() {
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn(null)

        val newProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        assertFalse(newProvider.isConnected())
    }

    @Test
    fun `isConnected should return true when user ID is stored`() {
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user-123")

        val newProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        assertTrue(newProvider.isConnected())
    }

    @Test
    fun `connect should return authorization URL`(): Unit = runBlocking {
        val expectedUrl = "https://connect.garmin.com/oauth2Confirm?client_id=xxx"

        mockWhen(mockApi.getAuthorizationUrl(
            vendor = "garmin",
            redirectUri = cloudConfig.redirectUri,
            state = anyString(),
            appId = cloudConfig.appId,
            userId = null
        )).thenReturn(Response.success(OAuthAuthorizeResponse(expectedUrl)))

        val result = provider.connect()

        assertTrue(result.contains("garmin"))
    }

    @Test
    fun `connect should throw exception on API error`(): Unit = runBlocking {
        mockWhen(mockApi.getAuthorizationUrl(
            vendor = eq("garmin"),
            redirectUri = anyString(),
            state = anyString(),
            appId = anyString(),
            userId = anyOrNull()
        )).thenReturn(Response.error(
            500,
            "Server error".toResponseBody("text/plain".toMediaType())
        ))

        try {
            provider.connect()
            fail("Expected SynheartWearException")
        } catch (e: SynheartWearException) {
            assertTrue(e.message?.contains("Failed") == true)
        }
    }

    @Test
    fun `handleDeepLinkCallback should store user ID on success`(): Unit = runBlocking {
        val userId = "garmin-user-456"

        val result = provider.handleDeepLinkCallback(
            success = true,
            userId = userId,
            error = null
        )

        assertEquals(userId, result)
        verify(mockSharedPrefsEditor).putString("user_id", userId)
    }

    @Test
    fun `handleDeepLinkCallback should throw exception on failure`(): Unit = runBlocking {
        try {
            provider.handleDeepLinkCallback(
                success = false,
                userId = null,
                error = "User denied access"
            )
            fail("Expected SynheartWearException")
        } catch (e: SynheartWearException) {
            assertTrue(e.message?.contains("User denied access") == true)
        }
    }

    @Test
    fun `disconnect should clear user ID`(): Unit = runBlocking {
        // Setup connected state
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user")

        val connectedProvider = GarminProvider(mockContext, cloudConfig, mockApi)

        mockWhen(mockApi.disconnect(
            vendor = "garmin",
            userId = "test-user",
            appId = cloudConfig.appId
        )).thenReturn(Response.success(DisconnectResponse("disconnected")))

        connectedProvider.disconnect()

        verify(mockSharedPrefsEditor).remove("user_id")
    }

    @Test
    fun `fetchDailies should return metrics when connected`(): Unit = runBlocking {
        // Setup connected state
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user")

        val mockEnvelope = GarminDataEnvelope(
            vendor = "garmin",
            appId = "test-app",
            userId = "test-user",
            summaryType = "dailies",
            records = listOf(
                mapOf(
                    "startTimeInSeconds" to 1704067200,  // 2024-01-01 00:00:00 UTC
                    "steps" to 10000.0,
                    "activeKilocalories" to 500.0,
                    "restingHeartRate" to 60.0,
                    "averageStressLevel" to 30.0
                )
            ),
            cursor = null
        )

        mockWhen(mockApi.getGarminData(
            userId = "test-user",
            summaryType = "dailies",
            appId = cloudConfig.appId,
            start = null,
            end = null
        )).thenReturn(Response.success(mockEnvelope))

        val connectedProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        val result = connectedProvider.fetchDailies()

        assertEquals(1, result.size)
        assertEquals("garmin_dailies", result[0].source)
    }

    @Test
    fun `fetchHRV should return HRV metrics`(): Unit = runBlocking {
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user")

        val mockEnvelope = GarminDataEnvelope(
            vendor = "garmin",
            appId = "test-app",
            userId = "test-user",
            summaryType = "hrv",
            records = listOf(
                mapOf(
                    "calendarDate" to "2024-01-01",
                    "lastNightAvg" to 45.0,
                    "lastNight5MinHigh" to 65.0,
                    "status" to "BALANCED"
                )
            ),
            cursor = null
        )

        mockWhen(mockApi.getGarminData(
            userId = "test-user",
            summaryType = "hrv",
            appId = cloudConfig.appId,
            start = null,
            end = null
        )).thenReturn(Response.success(mockEnvelope))

        val connectedProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        val result = connectedProvider.fetchHRV()

        assertEquals(1, result.size)
        assertTrue(result[0].hasMetric(ai.synheart.wear.models.MetricType.HRV_RMSSD))
    }

    @Test
    fun `fetchSleeps should return sleep metrics`(): Unit = runBlocking {
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user")

        val mockEnvelope = GarminDataEnvelope(
            vendor = "garmin",
            appId = "test-app",
            userId = "test-user",
            summaryType = "sleeps",
            records = listOf(
                mapOf(
                    "sleepStartTimestampGMT" to 1704067200000L,
                    "durationInSeconds" to 28800.0,  // 8 hours
                    "deepSleepDurationInSeconds" to 5400.0,  // 1.5 hours
                    "lightSleepDurationInSeconds" to 14400.0,  // 4 hours
                    "remSleepInSeconds" to 7200.0,  // 2 hours
                    "overallSleepScore" to 85.0
                )
            ),
            cursor = null
        )

        mockWhen(mockApi.getGarminData(
            userId = "test-user",
            summaryType = "sleeps",
            appId = cloudConfig.appId,
            start = null,
            end = null
        )).thenReturn(Response.success(mockEnvelope))

        val connectedProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        val result = connectedProvider.fetchSleeps()

        assertEquals(1, result.size)
        assertEquals("garmin_sleeps", result[0].source)
        assertEquals("8.0", result[0].meta["sleep_duration_hours"])
    }

    @Test
    fun `requestBackfill should return true on success`(): Unit = runBlocking {
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user")

        val mockResponse = GarminBackfillResponse(
            status = "accepted",
            message = "Backfill request accepted",
            userId = "test-user",
            summaryType = "dailies",
            start = "2024-01-01T00:00:00Z",
            end = "2024-03-01T00:00:00Z"
        )

        mockWhen(mockApi.requestGarminBackfill(
            userId = eq("test-user"),
            summaryType = eq("dailies"),
            request = any()
        )).thenReturn(Response.success(202, mockResponse))

        val connectedProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        val result = connectedProvider.requestBackfill(
            summaryType = GarminProvider.SummaryType.DAILIES,
            startDate = java.util.Date(),
            endDate = java.util.Date()
        )

        assertTrue(result)
    }

    @Test
    fun `getWebhookUrls should return URLs map`(): Unit = runBlocking {
        val mockResponse = GarminWebhookURLsResponse(
            appId = "test-app",
            baseUrl = "https://api.wear.synheart.ai",
            endpoints = mapOf(
                "dailies" to "https://api.wear.synheart.ai/v1/garmin/webhooks/test-app/dailies",
                "sleeps" to "https://api.wear.synheart.ai/v1/garmin/webhooks/test-app/sleeps",
                "hrv" to "https://api.wear.synheart.ai/v1/garmin/webhooks/test-app/hrv"
            ),
            instructions = "Configure these in Garmin Developer Portal"
        )

        mockWhen(mockApi.getGarminWebhookUrls(
            appId = cloudConfig.appId,
            baseUrl = null
        )).thenReturn(Response.success(mockResponse))

        val result = provider.getWebhookUrls()

        assertEquals(3, result.size)
        assertTrue(result.containsKey("dailies"))
        assertTrue(result.containsKey("sleeps"))
        assertTrue(result.containsKey("hrv"))
    }

    @Test
    fun `fetchRecovery should return HRV data for Garmin`(): Unit = runBlocking {
        // For Garmin, recovery maps to HRV data
        mockWhen(mockSharedPrefs.getString("user_id", null)).thenReturn("test-user")

        val mockEnvelope = GarminDataEnvelope(
            vendor = "garmin",
            appId = "test-app",
            userId = "test-user",
            summaryType = "hrv",
            records = listOf(
                mapOf(
                    "lastNightAvg" to 50.0,
                    "status" to "BALANCED"
                )
            ),
            cursor = null
        )

        mockWhen(mockApi.getGarminData(
            userId = "test-user",
            summaryType = "hrv",
            appId = cloudConfig.appId,
            start = null,
            end = null
        )).thenReturn(Response.success(mockEnvelope))

        val connectedProvider = GarminProvider(mockContext, cloudConfig, mockApi)
        val result = connectedProvider.fetchRecovery()

        assertEquals(1, result.size)
        assertEquals("garmin_hrv", result[0].source)
    }

    @Test
    fun `summary types should have correct values`() {
        assertEquals("dailies", GarminProvider.SummaryType.DAILIES.value)
        assertEquals("epochs", GarminProvider.SummaryType.EPOCHS.value)
        assertEquals("sleeps", GarminProvider.SummaryType.SLEEPS.value)
        assertEquals("stressDetails", GarminProvider.SummaryType.STRESS_DETAILS.value)
        assertEquals("hrv", GarminProvider.SummaryType.HRV.value)
        assertEquals("userMetrics", GarminProvider.SummaryType.USER_METRICS.value)
        assertEquals("bodyComps", GarminProvider.SummaryType.BODY_COMPS.value)
        assertEquals("pulseox", GarminProvider.SummaryType.PULSE_OX.value)
        assertEquals("respiration", GarminProvider.SummaryType.RESPIRATION.value)
        assertEquals("healthSnapshot", GarminProvider.SummaryType.HEALTH_SNAPSHOT.value)
        assertEquals("bloodPressures", GarminProvider.SummaryType.BLOOD_PRESSURES.value)
        assertEquals("skinTemp", GarminProvider.SummaryType.SKIN_TEMP.value)
    }
}

