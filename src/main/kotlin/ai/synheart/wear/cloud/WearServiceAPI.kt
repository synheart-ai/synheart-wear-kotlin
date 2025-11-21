package ai.synheart.wear.cloud

import ai.synheart.wear.cloud.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for Synheart Wear Service
 *
 * This interface defines all REST API endpoints for communicating
 * with the wear service backend for cloud-based wearable integration.
 */
interface WearServiceAPI {

    // ============= OAuth Endpoints =============

    /**
     * Get OAuth authorization URL for a vendor
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param redirectUri OAuth redirect URI
     * @param state OAuth state parameter for CSRF protection
     * @param appId Application ID
     * @param userId User ID (optional)
     */
    @GET("/v1/{vendor}/oauth/authorize")
    suspend fun getAuthorizationUrl(
        @Path("vendor") vendor: String,
        @Query("redirect_uri") redirectUri: String,
        @Query("state") state: String,
        @Query("app_id") appId: String,
        @Query("user_id") userId: String? = null
    ): Response<OAuthAuthorizeResponse>

    /**
     * Handle OAuth callback - exchange authorization code for tokens
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param request OAuth callback request containing code, state, and redirect_uri
     */
    @POST("/v1/{vendor}/oauth/callback")
    suspend fun handleOAuthCallback(
        @Path("vendor") vendor: String,
        @Body request: OAuthCallbackRequest
    ): Response<OAuthCallbackResponse>

    /**
     * Disconnect user from vendor
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param userId User ID
     * @param appId Application ID
     */
    @DELETE("/v1/{vendor}/oauth/disconnect")
    suspend fun disconnect(
        @Path("vendor") vendor: String,
        @Query("user_id") userId: String,
        @Query("app_id") appId: String
    ): Response<DisconnectResponse>

    // ============= Data Endpoints =============

    /**
     * Fetch recovery data for a user
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param userId User ID
     * @param appId Application ID
     * @param start Start time (RFC3339)
     * @param end End time (RFC3339)
     * @param limit Number of records (default: 100, max: 1000)
     * @param cursor Pagination cursor
     */
    @GET("/v1/{vendor}/data/{user_id}/recovery")
    suspend fun getRecoveryData(
        @Path("vendor") vendor: String,
        @Path("user_id") userId: String,
        @Query("app_id") appId: String,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): Response<RecoveryEnvelope>

    /**
     * Fetch sleep data for a user
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param userId User ID
     * @param appId Application ID
     * @param start Start time (RFC3339)
     * @param end End time (RFC3339)
     * @param limit Number of records (default: 100, max: 1000)
     * @param cursor Pagination cursor
     */
    @GET("/v1/{vendor}/data/{user_id}/sleep")
    suspend fun getSleepData(
        @Path("vendor") vendor: String,
        @Path("user_id") userId: String,
        @Query("app_id") appId: String,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): Response<SleepEnvelope>

    /**
     * Fetch workout data for a user
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param userId User ID
     * @param appId Application ID
     * @param start Start time (RFC3339)
     * @param end End time (RFC3339)
     * @param limit Number of records (default: 100, max: 1000)
     * @param cursor Pagination cursor
     */
    @GET("/v1/{vendor}/data/{user_id}/workouts")
    suspend fun getWorkoutData(
        @Path("vendor") vendor: String,
        @Path("user_id") userId: String,
        @Query("app_id") appId: String,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): Response<WorkoutEnvelope>

    /**
     * Fetch cycle data for a user
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param userId User ID
     * @param appId Application ID
     * @param start Start time (RFC3339)
     * @param end End time (RFC3339)
     * @param limit Number of records (default: 100, max: 1000)
     * @param cursor Pagination cursor
     */
    @GET("/v1/{vendor}/data/{user_id}/cycles")
    suspend fun getCycleData(
        @Path("vendor") vendor: String,
        @Path("user_id") userId: String,
        @Query("app_id") appId: String,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): Response<CycleEnvelope>

    // ============= Integration Endpoints =============

    /**
     * Create or update vendor integration
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param request Integration configuration
     */
    @POST("/v1/integrations/{vendor}")
    suspend fun upsertIntegration(
        @Path("vendor") vendor: String,
        @Body request: UpsertIntegrationRequest
    ): Response<IntegrationResponse>

    /**
     * Get vendor integration configuration
     *
     * @param vendor Vendor type (whoop, garmin, fitbit)
     * @param appId Application ID
     */
    @GET("/v1/integrations/{vendor}")
    suspend fun getIntegration(
        @Path("vendor") vendor: String,
        @Query("app_id") appId: String
    ): Response<IntegrationResponse>
}

