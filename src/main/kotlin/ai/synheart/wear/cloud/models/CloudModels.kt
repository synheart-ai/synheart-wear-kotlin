package ai.synheart.wear.cloud.models

import com.google.gson.annotations.SerializedName

/**
 * OAuth authorization response from wear service
 */
data class OAuthAuthorizeResponse(
    @SerializedName("authorization_url")
    val authorizationUrl: String
)

/**
 * OAuth callback request to exchange authorization code for tokens
 */
data class OAuthCallbackRequest(
    @SerializedName("code")
    val code: String,
    @SerializedName("state")
    val state: String,
    @SerializedName("redirect_uri")
    val redirectUri: String
)

/**
 * OAuth callback response with user_id
 */
data class OAuthCallbackResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("user_id")
    val userId: String
)

/**
 * Recovery data envelope from wear service
 */
data class RecoveryEnvelope(
    @SerializedName("vendor")
    val vendor: String,
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("records")
    val records: List<Map<String, Any>>,
    @SerializedName("cursor")
    val cursor: String?
)

/**
 * Sleep data envelope from wear service
 */
data class SleepEnvelope(
    @SerializedName("vendor")
    val vendor: String,
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("records")
    val records: List<Map<String, Any>>,
    @SerializedName("cursor")
    val cursor: String?
)

/**
 * Workout data envelope from wear service
 */
data class WorkoutEnvelope(
    @SerializedName("vendor")
    val vendor: String,
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("records")
    val records: List<Map<String, Any>>,
    @SerializedName("cursor")
    val cursor: String?
)

/**
 * Cycle data envelope from wear service
 */
data class CycleEnvelope(
    @SerializedName("vendor")
    val vendor: String,
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("records")
    val records: List<Map<String, Any>>,
    @SerializedName("cursor")
    val cursor: String?
)

/**
 * Disconnect response
 */
data class DisconnectResponse(
    @SerializedName("status")
    val status: String
)

/**
 * API Error response
 */
data class APIErrorResponse(
    @SerializedName("code")
    val code: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("details")
    val details: String? = null,
    @SerializedName("field")
    val field: String? = null
)

/**
 * Integration configuration request
 */
data class UpsertIntegrationRequest(
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("client_secret")
    val clientSecret: String,
    @SerializedName("webhook_secret")
    val webhookSecret: String? = null,
    @SerializedName("redirect_uri")
    val redirectUri: String,
    @SerializedName("scopes")
    val scopes: List<String>,
    @SerializedName("app_webhook_url")
    val appWebhookUrl: String? = null
)

/**
 * Integration configuration response
 */
data class IntegrationResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("vendor")
    val vendor: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("redirect_uri")
    val redirectUri: String,
    @SerializedName("scopes")
    val scopes: List<String>,
    @SerializedName("app_webhook_url")
    val appWebhookUrl: String? = null,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

