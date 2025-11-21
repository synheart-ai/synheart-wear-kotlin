package ai.synheart.wear.adapters

import ai.synheart.wear.SynheartWearException
import ai.synheart.wear.cloud.WearServiceAPI
import ai.synheart.wear.cloud.models.*
import ai.synheart.wear.config.CloudConfig
import ai.synheart.wear.models.DeviceAdapter
import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for CloudWearableAdapter
 *
 * Tests OAuth flow, adapter lifecycle, and integration management
 */
class CloudWearableAdapterTest {

    private lateinit var context: Context
    private lateinit var cloudConfig: CloudConfig
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var sharedPrefsEditor: SharedPreferences.Editor
    private lateinit var adapter: CloudWearableAdapter
    private lateinit var mockApi: WearServiceAPI

    @Before
    fun setup() {
        // Mock context and shared preferences
        context = mockk(relaxed = true)
        sharedPrefs = mockk(relaxed = true)
        sharedPrefsEditor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns sharedPrefsEditor
        every { sharedPrefsEditor.putString(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.remove(any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.apply() } just Runs
        every { sharedPrefs.getString(any(), any()) } returns null

        // Create cloud config
        cloudConfig = CloudConfig(
            baseUrl = "https://test-api.synheart.io",
            appId = "test-app-id",
            redirectUri = "synheart://oauth/callback",
            enableDebugLogging = false
        )

        // Mock the API
        mockApi = mockk()

        // Create adapter with mocked API
        adapter = CloudWearableAdapter(context, DeviceAdapter.WHOOP, cloudConfig, mockApi)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== Initialization Tests ==========

    @Test
    fun `initialize should set initialized flag`() = runTest {
        // When
        adapter.initialize()

        // Then
        // No exception thrown means success
    }

    @Test
    fun `initialize should load existing userId from storage`() = runTest {
        // Given
        val existingUserId = "user_123"
        every { sharedPrefs.getString("user_id", null) } returns existingUserId

        // When
        adapter.initialize()

        // Then
        assertEquals(existingUserId, adapter.getUserId())
        assertTrue(adapter.isConnectedToCloud())
    }

    @Test
    fun `adapter id should include vendor name`() {
        // When
        val id = adapter.id

        // Then
        assertTrue(id.contains("whoop"))
    }

    // ========== OAuth Flow Tests ==========

    @Test
    fun `startOAuthFlow should return authorization URL`() = runTest {
        // Given
        adapter.initialize()
        val expectedAuthUrl = "https://api.prod.whoop.com/oauth/oauth2/auth?client_id=test"
        val response = Response.success(OAuthAuthorizeResponse(expectedAuthUrl))
        coEvery { mockApi.getAuthorizationUrl(any(), any(), any(), any(), any()) } returns response

        // When
        val authUrl = adapter.startOAuthFlow()

        // Then
        assertEquals(expectedAuthUrl, authUrl)
        assertNotNull(authUrl)
    }

    @Test
    fun `startOAuthFlow should use redirectUri from config by default`() = runTest {
        // Given
        adapter.initialize()
        val authUrl = "https://api.prod.whoop.com/oauth/oauth2/auth"
        val response = Response.success(OAuthAuthorizeResponse(authUrl))
        
        coEvery { 
            mockApi.getAuthorizationUrl(any(), any(), any(), any(), any()) 
        } returns response

        // When
        adapter.startOAuthFlow()

        // Then - default redirectUri from config should be used
        coVerify { 
            mockApi.getAuthorizationUrl(any(), cloudConfig.redirectUri, any(), any(), any())
        }
    }

    @Test(expected = SynheartWearException::class)
    fun `startOAuthFlow should throw exception when not initialized`() = runTest {
        // Given - adapter not initialized

        // When
        adapter.startOAuthFlow()

        // Then - exception thrown
    }

    @Test
    fun `completeOAuthFlow should exchange code and save userId`() = runTest {
        // Given
        adapter.initialize()
        val code = "auth_code_123"
        val state = "state_abc"
        val userId = "user_456"

        every { sharedPrefs.getString(match { it == "oauth_state" }, any()) } returns state

        val callbackResponse = Response.success(
            OAuthCallbackResponse(status = "success", userId = userId)
        )
        coEvery { mockApi.handleOAuthCallback(any(), any()) } returns callbackResponse

        // When
        val returnedUserId = adapter.completeOAuthFlow(code, state)

        // Then
        assertEquals(userId, returnedUserId)
        assertEquals(userId, adapter.getUserId())
        assertTrue(adapter.isConnectedToCloud())
        verify { sharedPrefsEditor.putString("user_id", userId) }
    }

    @Test(expected = SynheartWearException::class)
    fun `completeOAuthFlow should throw exception on state mismatch`() = runTest {
        // Given
        adapter.initialize()
        val code = "auth_code_123"
        val state = "state_abc"
        val differentState = "different_state"

        every { sharedPrefs.getString("oauth_state", null) } returns differentState

        // When
        adapter.completeOAuthFlow(code, state)

        // Then - exception thrown
    }

    @Test
    fun `disconnect should clear userId and update connection status`() = runTest {
        // Given
        adapter.initialize()
        setPrivateField(adapter, "userId", "user_123")
        setPrivateField(adapter, "isConnected", true)

        val disconnectResponse = Response.success(DisconnectResponse(status = "disconnected"))
        coEvery { mockApi.disconnect(any(), any(), any()) } returns disconnectResponse

        // When
        adapter.disconnect()

        // Then
        assertFalse(adapter.isConnectedToCloud())
        assertEquals(null, adapter.getUserId())
        verify { sharedPrefsEditor.remove("user_id") }
    }

    // ========== Data Fetching Tests ==========

    @Test
    fun `fetchRecoveryData should return list of records`() = runTest {
        // Given
        adapter.initialize()
        setPrivateField(adapter, "userId", "user_456")
        setPrivateField(adapter, "isConnected", true)

        val records = listOf(
            mapOf("score" to 85.0, "hrv" to 50.0),
            mapOf("score" to 90.0, "hrv" to 55.0)
        )

        val envelope = RecoveryEnvelope(
            vendor = "whoop",
            appId = cloudConfig.appId,
            userId = "user_456",
            records = records,
            cursor = null
        )

        val response = Response.success(envelope)
        coEvery { mockApi.getRecoveryData(any(), any(), any(), any(), any(), any(), any()) } returns response

        // When
        val result = adapter.fetchRecoveryData()

        // Then
        assertNotNull(result)
        assertEquals(2, result.size)
    }

    @Test(expected = SynheartWearException::class)
    fun `fetchRecoveryData should throw exception when not connected`() = runTest {
        // Given
        adapter.initialize()
        setPrivateField(adapter, "userId", null)
        setPrivateField(adapter, "isConnected", false)

        // When
        adapter.fetchRecoveryData()

        // Then - exception thrown
    }

    @Test
    fun `fetchSleepData should return list of records`() = runTest {
        // Given
        adapter.initialize()
        setPrivateField(adapter, "userId", "user_456")
        setPrivateField(adapter, "isConnected", true)

        val records = listOf(
            mapOf("duration" to 28800.0, "score" to 90.0)
        )

        val envelope = SleepEnvelope(
            vendor = "whoop",
            appId = cloudConfig.appId,
            userId = "user_456",
            records = records,
            cursor = null
        )

        val response = Response.success(envelope)
        coEvery { mockApi.getSleepData(any(), any(), any(), any(), any(), any(), any()) } returns response

        // When
        val result = adapter.fetchSleepData()

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    // ========== Availability Tests ==========

    @Test
    fun `isAvailable should return true when integration exists`() = runTest {
        // Given
        adapter.initialize()
        val integrationResponse = Response.success(
            IntegrationResponse(
                id = "int_123",
                appId = cloudConfig.appId,
                vendor = "whoop",
                status = "active",
                redirectUri = cloudConfig.redirectUri,
                scopes = listOf("read:recovery", "read:sleep"),
                appWebhookUrl = null,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z"
            )
        )
        coEvery { mockApi.getIntegration(any(), any()) } returns integrationResponse

        // When
        val available = adapter.isAvailable()

        // Then
        assertTrue(available)
    }

    @Test
    fun `isAvailable should return false when integration request fails`() = runTest {
        // Given
        adapter.initialize()
        val errorResponse = Response.error<IntegrationResponse>(
            404,
            "Not found".toResponseBody()
        )
        coEvery { mockApi.getIntegration(any(), any()) } returns errorResponse

        // When
        val available = adapter.isAvailable()

        // Then
        assertFalse(available)
    }

    @Test
    fun `isAvailable should return false on network exception`() = runTest {
        // Given
        adapter.initialize()
        coEvery { mockApi.getIntegration(any(), any()) } throws Exception("Network error")

        // When
        val available = adapter.isAvailable()

        // Then
        assertFalse(available)
    }

    // ========== Permission Tests ==========

    @Test
    fun `requestPermissions should return granted when connected`() = runTest {
        // Given
        adapter.initialize()
        setPrivateField(adapter, "isConnected", true)

        // When
        val permissions = adapter.requestPermissions(
            setOf(
                ai.synheart.wear.models.PermissionType.HEART_RATE,
                ai.synheart.wear.models.PermissionType.HRV
            )
        )

        // Then
        assertTrue(permissions.all { it.value })
    }

    @Test
    fun `requestPermissions should return denied when not connected`() = runTest {
        // Given
        adapter.initialize()
        setPrivateField(adapter, "isConnected", false)

        // When
        val permissions = adapter.requestPermissions(
            setOf(
                ai.synheart.wear.models.PermissionType.HEART_RATE,
                ai.synheart.wear.models.PermissionType.HRV
            )
        )

        // Then
        assertTrue(permissions.all { !it.value })
    }

    @Test
    fun `getPermissionStatus should return all granted when connected`() {
        // Given
        setPrivateField(adapter, "isConnected", true)

        // When
        val status = adapter.getPermissionStatus()

        // Then
        assertTrue(status.all { it.value })
    }

    // ========== Helper Methods ==========

    private fun setPrivateField(obj: Any, fieldName: String, value: Any?) {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }
}

