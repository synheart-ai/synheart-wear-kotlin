# Synheart Wear - Android SDK

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android API 21+](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8%2B-blue.svg)](https://kotlinlang.org)
[![JitPack](https://jitpack.io/v/synheart-ai/synheart-wear-android.svg)](https://jitpack.io/#synheart-ai/synheart-wear-android)

**Unified wearable SDK for Android** — Stream biometric data from Apple Watch, Fitbit, Garmin, Whoop, and Samsung devices via Health Connect with a single standardized API.

## 🚀 Features

- **📱 Health Connect Integration**: Native Android biometric data access
- **⌚ Multi-Device Support**: Apple Watch, Fitbit, Garmin, Whoop, Samsung Watch
- **☁️ Cloud Wearables**: Direct integration with WHOOP, Garmin, and Fitbit cloud APIs
- **🔄 Real-Time Streaming**: Live HR and HRV data streams
- **📊 Unified Schema**: Consistent data format across all devices
- **🔒 Privacy-First**: Consent-based data access with AES-256 encryption
- **💾 Local Storage**: Encrypted offline data persistence
- **⚡ Kotlin Coroutines**: Modern async API with Flow support

## 📦 Installation

### Gradle (Kotlin DSL) - JitPack

**Step 1:** Add JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Or in your root `build.gradle.kts`:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**Step 2:** Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.synheart-ai:synheart-wear-android:0.2.0")
}
```

> **Note**: Replace `0.2.0` with the latest release version from [JitPack](https://jitpack.io/#synheart-ai/synheart-wear-android) or use `main-SNAPSHOT` for the latest development version.

### Requirements

- **Android SDK**: API 21+ (Android 5.0 Lollipop)
- **Target SDK**: API 34+ (Android 14)
- **Kotlin**: 1.8+
- **Health Connect**: Required for biometric data access (must be installed on device)
- **Gradle**: 7.0+ (for Kotlin DSL support)

## 🧬 Flux Installation (Native)

Flux is an **optional native (Rust) library** used by this SDK to produce **HSI outputs** via JNI (see `ai.synheart.wear.flux.*`). If Flux isn’t present at runtime, the SDK **gracefully degrades** (Flux APIs return `null` / `isAvailable=false`) and the rest of the SDK still works.

### When you need to do anything

- **Using the SDK via JitPack**: nothing to do (Flux is bundled in the published artifact).
- **Building / publishing this repo from source**: you must ensure the Flux `.so` binaries exist under `vendor/flux/android/jniLibs/`.

### Pinned Flux version

The Flux version used by this repo is pinned in `vendor/flux/VERSION` (currently `v0.1.0`).

### Manual install (dev machines)

1. Download `synheart-flux-android-jniLibs.tar.gz` from the Flux releases matching `vendor/flux/VERSION`:
   - [synheart-flux releases](https://github.com/synheart-ai/synheart-flux/releases)
2. Extract into this repo:

```bash
tar -xzf synheart-flux-android-jniLibs.tar.gz -C vendor/flux/android/jniLibs/
```

After extraction you should have:

- `vendor/flux/android/jniLibs/arm64-v8a/libsynheart_flux.so`
- `vendor/flux/android/jniLibs/armeabi-v7a/libsynheart_flux.so`
- `vendor/flux/android/jniLibs/x86_64/libsynheart_flux.so`

### CI/CD (recommended)

CI should fetch Flux artifacts based on `vendor/flux/VERSION` right before building/publishing:

```bash
FLUX_VERSION=$(cat vendor/flux/VERSION)
FLUX_BASE_URL="https://github.com/synheart-ai/synheart-flux/releases/download/${FLUX_VERSION}"

curl -L "${FLUX_BASE_URL}/synheart-flux-android-jniLibs.tar.gz" -o /tmp/flux-android.tar.gz
tar -xzf /tmp/flux-android.tar.gz -C vendor/flux/android/jniLibs/
```

### Runtime verification / troubleshooting

- Check availability: `SynheartWear.isFluxNativeAvailable()` (or `ai.synheart.wear.flux.FluxFfi.isAvailable`)
- If it fails to load: `ai.synheart.wear.flux.FluxFfi.getLoadError()`

### Enabling Flux in the SDK

Flux is opt-in. Enable it via `SynheartWearConfig(enableFlux = true)` and then call `readFluxSnapshot(...)` / `readFluxSnapshots(...)`.

For more details (including artifact names and directory structure), see `vendor/flux/README.md`.

## 🎯 Quick Start

### 1. Initialize the SDK

#### Local Wearables (Health Connect, Samsung Health)

```kotlin
import ai.synheart.wear.SynheartWear
import ai.synheart.wear.config.SynheartWearConfig
import ai.synheart.wear.models.DeviceAdapter

class MyApplication : Application() {
    lateinit var synheartWear: SynheartWear

    override fun onCreate() {
        super.onCreate()

        synheartWear = SynheartWear(
            context = this,
            config = SynheartWearConfig(
                enabledAdapters = setOf(
                    DeviceAdapter.HEALTH_CONNECT,
                    DeviceAdapter.SAMSUNG_HEALTH
                ),
                enableLocalCaching = true,
                enableEncryption = true,
                streamInterval = 3000L // 3 seconds
            )
        )
    }
}
```

#### Cloud Wearables (WHOOP, Garmin, Fitbit)

```kotlin
import ai.synheart.wear.SynheartWear
import ai.synheart.wear.config.SynheartWearConfig
import ai.synheart.wear.config.CloudConfig
import ai.synheart.wear.models.DeviceAdapter

class MyApplication : Application() {
    lateinit var synheartWear: SynheartWear

    override fun onCreate() {
        super.onCreate()

        synheartWear = SynheartWear(
            context = this,
            config = SynheartWearConfig(
                enabledAdapters = setOf(
                    DeviceAdapter.HEALTH_CONNECT,
                    DeviceAdapter.WHOOP,  // Cloud wearable
                    DeviceAdapter.GARMIN  // Cloud wearable
                ),
                enableLocalCaching = true,
                enableEncryption = true,
                streamInterval = 3000L,
                cloudConfig = CloudConfig(
                    baseUrl = "https://api.wear.synheart.io/v1",
                    appId = "your-app-id",  // Get from Synheart Dashboard
                    organizationId = "your-org-id",  // Optional
                    enableDebugLogging = false
                )
            )
        )
    }
}
```

### 2. Request Permissions

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var synheartWear: SynheartWear

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                // Initialize SDK
                synheartWear.initialize()

                // Request permissions
                val permissions = synheartWear.requestPermissions(
                    setOf(
                        PermissionType.HEART_RATE,
                        PermissionType.HRV,
                        PermissionType.STEPS
                    )
                )

                if (permissions[PermissionType.HEART_RATE] == true) {
                    Log.d(TAG, "Heart rate permission granted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize", e)
            }
        }
    }
}
```

### 3. Read Metrics

```kotlin
lifecycleScope.launch {
    try {
        val metrics = synheartWear.readMetrics()

        Log.d(TAG, "Heart Rate: ${metrics.getMetric(MetricType.HR)} bpm")
        Log.d(TAG, "HRV RMSSD: ${metrics.getMetric(MetricType.HRV_RMSSD)} ms")
        Log.d(TAG, "Steps: ${metrics.getMetric(MetricType.STEPS)}")
        Log.d(TAG, "Calories: ${metrics.getMetric(MetricType.CALORIES)}")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read metrics", e)
    }
}
```

### 4. Stream Real-Time Data

```kotlin
// Stream heart rate data every 3 seconds
lifecycleScope.launch {
    synheartWear.streamHR(intervalMs = 3000L)
        .collect { metrics ->
            val hr = metrics.getMetric(MetricType.HR)
            Log.d(TAG, "Live HR: $hr bpm")
        }
}

// Stream HRV data in 5-second windows
lifecycleScope.launch {
    synheartWear.streamHRV(windowMs = 5000L)
        .collect { metrics ->
            val hrvRmssd = metrics.getMetric(MetricType.HRV_RMSSD)
            val hrvSdnn = metrics.getMetric(MetricType.HRV_SDNN)
            Log.d(TAG, "HRV RMSSD: $hrvRmssd ms, SDNN: $hrvSdnn ms")
        }
}
```

## 🏥 Health Connect Integration

The SDK provides comprehensive integration with Google's Health Connect, allowing you to read biometric data from any wearable device that syncs to Health Connect (Apple Watch, Fitbit, Garmin, Samsung Watch, etc.).

### Overview

Health Connect is Android's unified platform for health and fitness data. It aggregates data from multiple wearable apps and devices, providing a single API to access all biometric data with user consent.

**Benefits:**
- ✅ Access data from multiple wearable devices simultaneously
- ✅ User-controlled permissions with granular access
- ✅ Unified data format across all devices
- ✅ Automatic data synchronization
- ✅ Privacy-first design with user consent

### Quick Start with Health Connect

#### 1. Check Health Connect Availability

```kotlin
import ai.synheart.wear.adapters.HealthConnectAdapter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if Health Connect is available
        val status = HealthConnectAdapter.getSdkStatus(this)
        when (status) {
            HealthConnectClient.SDK_AVAILABLE -> {
                Log.d(TAG, "Health Connect is available!")
            }
            HealthConnectClient.SDK_UNAVAILABLE -> {
                Log.w(TAG, "Health Connect not installed")
                // Direct user to install Health Connect from Play Store
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Log.w(TAG, "Health Connect update required")
                // Direct user to update Health Connect
            }
        }
    }
}
```

#### 2. Request Permissions

Health Connect requires explicit user consent for each data type. Use the Activity Result API to request permissions:

```kotlin
import ai.synheart.wear.adapters.HealthConnectAdapter
import ai.synheart.wear.models.PermissionType
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectAdapter: HealthConnectAdapter
    
    // Register the permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        healthConnectAdapter.getPermissionRequestContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            Log.d(TAG, "All permissions granted!")
            lifecycleScope.launch {
                readHealthData()
            }
        } else {
            Log.w(TAG, "Some permissions were denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            healthConnectAdapter = HealthConnectAdapter(this@MainActivity)
            healthConnectAdapter.initialize()
            
            // Define required permissions
            val permissions = healthConnectAdapter.getHealthConnectPermissions(
                setOf(
                    PermissionType.HEART_RATE,
                    PermissionType.HRV,
                    PermissionType.STEPS,
                    PermissionType.CALORIES,
                    PermissionType.DISTANCE,
                    PermissionType.EXERCISE,
                    PermissionType.SLEEP
                )
            )
            
            // Check if permissions are already granted
            if (!healthConnectAdapter.hasAllPermissions(permissions)) {
                // Request permissions
                requestPermissionLauncher.launch(permissions)
            } else {
                // Permissions already granted
                readHealthData()
            }
        }
    }
}
```

#### 3. Read Health Data

Once permissions are granted, you can read various types of health data:

```kotlin
import ai.synheart.wear.adapters.HealthConnectManager
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthDataRepository(context: Context) {
    private val healthConnectManager = HealthConnectManager(context)
    
    // Read heart rate data
    suspend fun getHeartRateData(): HeartRateData? {
        val now = Instant.now()
        val start = now.minus(1, ChronoUnit.HOURS)
        
        val records = healthConnectManager.readHeartRate(start, now)
        val avgHR = healthConnectManager.readAverageHeartRate(start, now)
        val hrRange = healthConnectManager.readHeartRateRange(start, now)
        
        return HeartRateData(
            records = records,
            average = avgHR,
            min = hrRange.min,
            max = hrRange.max
        )
    }
    
    // Read HRV data
    suspend fun getHRVData(): Double? {
        val now = Instant.now()
        val start = now.minus(24, ChronoUnit.HOURS)
        
        return healthConnectManager.readAverageHRV(start, now)
    }
    
    // Read daily steps
    suspend fun getTodaySteps(): Long {
        val timeRange = healthConnectManager.getTimeRangeToday()
        return healthConnectManager.readStepsTotal(
            timeRange.startTime ?: Instant.now(),
            timeRange.endTime ?: Instant.now()
        )
    }
    
    // Read calories burned
    suspend fun getTodayCalories(): Double {
        val now = Instant.now()
        val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
        
        return healthConnectManager.readCaloriesTotal(startOfDay, now)
    }
    
    // Read exercise sessions
    suspend fun getRecentExercises(): List<ExerciseSessionRecord> {
        val now = Instant.now()
        val lastWeek = now.minus(7, ChronoUnit.DAYS)
        
        return healthConnectManager.readExerciseSessions(lastWeek, now)
    }
    
    // Read sleep data
    suspend fun getRecentSleep(): List<SleepSessionRecord> {
        val now = Instant.now()
        val lastWeek = now.minus(7, ChronoUnit.DAYS)
        
        return healthConnectManager.readSleepSessions(lastWeek, now)
    }
}
```

### Available Data Types

The SDK supports reading the following Health Connect data types:

| Data Type | Permission | Description |
|-----------|-----------|-------------|
| **Heart Rate** | `READ_HEART_RATE` | Real-time heart rate measurements |
| **HRV** | `READ_HEART_RATE_VARIABILITY` | Heart rate variability (RMSSD) |
| **Resting HR** | `READ_RESTING_HEART_RATE` | Resting heart rate measurements |
| **Steps** | `READ_STEPS` | Step count data |
| **Calories** | `READ_TOTAL_CALORIES_BURNED` | Total and active calories burned |
| **Distance** | `READ_DISTANCE` | Distance traveled |
| **Exercise** | `READ_EXERCISE` | Exercise sessions with details |
| **Sleep** | `READ_SLEEP` | Sleep sessions with stages |
| **SpO2** | `READ_OXYGEN_SATURATION` | Blood oxygen saturation |
| **Respiratory Rate** | `READ_RESPIRATORY_RATE` | Breathing rate |
| **Body Temperature** | `READ_BODY_TEMPERATURE` | Body temperature readings |
| **Weight** | `READ_WEIGHT` | Weight measurements |
| **Height** | `READ_HEIGHT` | Height measurements |
| **Body Fat** | `READ_BODY_FAT` | Body fat percentage |

### Aggregation Queries

Health Connect supports aggregation for efficient data queries:

```kotlin
import ai.synheart.wear.adapters.HealthConnectManager

class HealthStatsViewModel(context: Context) {
    private val manager = HealthConnectManager(context)
    
    suspend fun getWeeklyStats(): WeeklyStats {
        val now = Instant.now()
        val weekAgo = now.minus(7, ChronoUnit.DAYS)
        
        return WeeklyStats(
            totalSteps = manager.readStepsTotal(weekAgo, now),
            totalCalories = manager.readCaloriesTotal(weekAgo, now),
            totalDistance = manager.readDistanceTotal(weekAgo, now),
            avgHeartRate = manager.readAverageHeartRate(weekAgo, now),
            avgHRV = manager.readAverageHRV(weekAgo, now),
            totalSleepHours = (manager.readSleepDuration(weekAgo, now) ?: 0L) / (1000 * 60 * 60)
        )
    }
    
    data class WeeklyStats(
        val totalSteps: Long,
        val totalCalories: Double,
        val totalDistance: Double,
        val avgHeartRate: Double?,
        val avgHRV: Double?,
        val totalSleepHours: Long
    )
}
```

### Real-Time Data Monitoring

Monitor health data in real-time using Kotlin Flow:

```kotlin
import kotlinx.coroutines.flow.*

class HealthMonitor(context: Context) {
    private val synheartWear = SynheartWear(
        context = context,
        config = SynheartWearConfig(
            enabledAdapters = setOf(DeviceAdapter.HEALTH_CONNECT),
            streamInterval = 5000L // 5 seconds
        )
    )
    
    // Stream heart rate data
    fun monitorHeartRate(): Flow<Double?> = 
        synheartWear.streamHR(intervalMs = 5000L)
            .map { it.getMetric(MetricType.HR) }
    
    // Stream HRV data
    fun monitorHRV(): Flow<Double?> = 
        synheartWear.streamHRV(windowMs = 10000L)
            .map { it.getMetric(MetricType.HRV_RMSSD) }
    
    // Combined monitoring
    fun monitorVitals(): Flow<VitalsData> = 
        synheartWear.streamHR(intervalMs = 3000L)
            .map { metrics ->
                VitalsData(
                    heartRate = metrics.getMetric(MetricType.HR),
                    hrv = metrics.getMetric(MetricType.HRV_RMSSD),
                    steps = metrics.getMetric(MetricType.STEPS)?.toLong(),
                    timestamp = Instant.ofEpochMilli(metrics.timestamp)
                )
            }
    
    data class VitalsData(
        val heartRate: Double?,
        val hrv: Double?,
        val steps: Long?,
        val timestamp: Instant
    )
}
```

### Differential Changes API

Efficiently sync only new or changed data using the Changes API:

```kotlin
import ai.synheart.wear.adapters.HealthConnectManager
import androidx.health.connect.client.records.*

class HealthDataSync(context: Context) {
    private val manager = HealthConnectManager(context)
    private var changesToken: String? = null
    
    suspend fun syncChanges() {
        // Get initial token if we don't have one
        if (changesToken == null) {
            changesToken = manager.getChangesToken(
                setOf(
                    HeartRateRecord::class,
                    StepsRecord::class,
                    SleepSessionRecord::class
                )
            )
        }
        
        // Get changes since last sync
        manager.getChanges(changesToken!!).collect { message ->
            when (message) {
                is HealthConnectManager.ChangesMessage.ChangeList -> {
                    message.changes.forEach { change ->
                        when (change) {
                            is UpsertionChange -> {
                                // Handle new or updated record
                                Log.d(TAG, "New/Updated record: ${change.record}")
                            }
                            is DeletionChange -> {
                                // Handle deleted record
                                Log.d(TAG, "Deleted record: ${change.recordId}")
                            }
                        }
                    }
                }
                is HealthConnectManager.ChangesMessage.NoMoreChanges -> {
                    // Save token for next sync
                    changesToken = message.nextChangesToken
                    Log.d(TAG, "Sync complete. Next token: $changesToken")
                }
            }
        }
    }
}
```

### User Interface Integration

Display Health Connect data in Jetpack Compose:

```kotlin
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun HealthDashboard(viewModel: HealthViewModel) {
    val heartRate by viewModel.heartRate.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val calories by viewModel.calories.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Health Data", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HealthMetricCard(
            title = "Heart Rate",
            value = heartRate?.let { "%.0f bpm".format(it) } ?: "—",
            icon = Icons.Default.Favorite
        )
        
        HealthMetricCard(
            title = "Steps",
            value = steps?.let { "%,d".format(it) } ?: "—",
            icon = Icons.Default.DirectionsWalk
        )
        
        HealthMetricCard(
            title = "Calories",
            value = calories?.let { "%.0f cal".format(it) } ?: "—",
            icon = Icons.Default.LocalFireDepartment
        )
    }
}

@Composable
fun HealthMetricCard(title: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title)
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge)
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
```

### Best Practices

1. **Check Availability First**: Always check if Health Connect is available before making API calls
2. **Request Minimal Permissions**: Only request permissions you actually need
3. **Handle Permission Denials**: Gracefully handle cases where users deny permissions
4. **Use Aggregation**: Use aggregation queries for better performance when you don't need individual records
5. **Implement Caching**: Cache data locally to reduce API calls and improve performance
6. **Monitor Battery Usage**: Be mindful of streaming intervals to avoid excessive battery drain
7. **Handle Errors**: Health Connect may be unavailable or return errors - always handle exceptions
8. **Respect Privacy**: Never log sensitive health data, follow HIPAA/GDPR guidelines

### Troubleshooting

**Health Connect not available:**
```kotlin
if (!HealthConnectAdapter.isAvailable(context)) {
    // Direct user to install Health Connect
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
    }
    startActivity(intent)
}
```

**No data available:**
- Ensure the user has a wearable device connected
- Check that the wearable app syncs to Health Connect
- Verify permissions are granted
- Try a wider time range

**Permission errors:**
- Re-request permissions using the Activity Result API
- Check that all required permissions are declared in AndroidManifest.xml

## ☁️ Cloud Wearables Integration

The SDK supports direct integration with cloud-based wearables (WHOOP, Garmin, Fitbit) through the Synheart Wear Service backend.

### Connecting to Cloud Wearables

#### 1. Get Cloud Adapter

```kotlin
val whoopAdapter = synheartWear.getCloudAdapter(DeviceAdapter.WHOOP)
    ?: throw Exception("WHOOP adapter not enabled")
```

#### 2. Start OAuth Flow

```kotlin
// Start OAuth authorization
val authUrl = whoopAdapter.startOAuthFlow(
    redirectUri = "myapp://oauth/callback",  // Your app's deep link
    state = UUID.randomUUID().toString()
)

// Open authorization URL in browser
val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
startActivity(intent)
```

#### 3. Handle OAuth Callback

```kotlin
// In your deep link handler (e.g., in MainActivity)
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    
    intent?.data?.let { uri ->
        if (uri.scheme == "myapp" && uri.host == "oauth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            
            if (code != null && state != null) {
                lifecycleScope.launch {
                    try {
                        val userId = whoopAdapter.completeOAuthFlow(
                            code = code,
                            state = state,
                            redirectUri = "myapp://oauth/callback"
                        )
                        Log.d(TAG, "Successfully connected! User ID: $userId")
                    } catch (e: Exception) {
                        Log.e(TAG, "OAuth failed: ${e.message}")
                    }
                }
            }
        }
    }
}
```

#### 4. Configure Deep Link in AndroidManifest.xml

```xml
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="myapp"
            android:host="oauth"
            android:pathPrefix="/callback" />
    </intent-filter>
</activity>
```

### Fetching Cloud Data

Once connected, you can fetch data from cloud wearables:

```kotlin
// Check if connected
if (whoopAdapter.isConnectedToCloud()) {
    // Fetch recovery data
    val recoveryData = whoopAdapter.fetchRecoveryData(
        startDate = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000),
        endDate = Date(),
        limit = 25
    )
    
    // Fetch sleep data
    val sleepData = whoopAdapter.fetchSleepData(
        startDate = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000),
        endDate = Date(),
        limit = 25
    )
    
    Log.d(TAG, "Recovery records: ${recoveryData.size}")
    Log.d(TAG, "Sleep records: ${sleepData.size}")
}
```

### Disconnecting

```kotlin
// Disconnect from cloud wearable
whoopAdapter.disconnect()
```

### Unified Data Access

Cloud wearables work seamlessly with the SDK's unified API:

```kotlin
// Read metrics from all sources (local + cloud)
val metrics = synheartWear.readMetrics()
Log.d(TAG, "Heart Rate: ${metrics.getMetric(MetricType.HR)} bpm")

// Stream data (includes cloud sources)
synheartWear.streamHR().collect { metrics ->
    Log.d(TAG, "Live HR: ${metrics.getMetric(MetricType.HR)} bpm")
}
```

## 📊 Data Schema

All wearable data follows the **Synheart Data Schema v1.0**:

```json
{
  "timestamp": "2025-10-20T18:30:00Z",
  "device_id": "healthconnect_1234",
  "source": "health_connect",
  "metrics": {
    "hr": 72,
    "hrv_rmssd": 45,
    "hrv_sdnn": 62,
    "steps": 1045,
    "calories": 120.4,
    "stress": 0.3
  },
  "meta": {
    "battery": 0.82,
    "synced": true
  }
}
```

## 🔧 API Reference

### Core Methods

| Method | Description |
|--------|-------------|
| `suspend fun initialize()` | Request permissions & setup adapters |
| `suspend fun readMetrics(): WearMetrics` | Get current biometric snapshot |
| `fun streamHR(intervalMs): Flow<WearMetrics>` | Stream real-time heart rate |
| `fun streamHRV(windowMs): Flow<WearMetrics>` | Stream HRV in configurable windows |
| `suspend fun getCachedSessions(): List<WearMetrics>` | Retrieve cached wearable data |
| `suspend fun clearOldCache(maxAgeMs)` | Clean up old cached data |

### Permission Management

```kotlin
// Request specific permissions
val permissions = synheartWear.requestPermissions(
    setOf(PermissionType.HEART_RATE, PermissionType.STEPS)
)

// Check permission status
val status = synheartWear.getPermissionStatus()
Log.d(TAG, "HR permission: ${status[PermissionType.HEART_RATE]}")
```

### Local Storage

```kotlin
// Get cached sessions
val sessions = synheartWear.getCachedSessions(
    startDateMs = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L,
    limit = 100
)

// Get cache statistics
val stats = synheartWear.getCacheStats()
Log.d(TAG, "Total sessions: ${stats["total_sessions"]}")

// Clear old data
synheartWear.clearOldCache(maxAgeMs = 30 * 24 * 60 * 60 * 1000L)
```

## ⌚ Supported Devices

| Device | Platform | Integration | Status |
|--------|----------|-------------|--------|
| Apple Watch | Android | Health Connect | ✅ Ready |
| Fitbit | Android | Health Connect + Cloud API | ✅ Ready (Both) |
| Garmin | Android | Health Connect + Cloud API | ✅ Ready (Both) |
| WHOOP | Android | Cloud API | ✅ Ready |
| Samsung Watch | Android | Samsung Health SDK | ✅ Ready |
| Google Fit | Android | Health Connect | ✅ Ready |

### Integration Methods

- **Health Connect**: Native Android integration for local device data
- **Cloud API**: Direct integration with vendor cloud services via Synheart Wear Service backend
- **Samsung Health SDK**: Native Samsung Health integration

## 🔒 Privacy & Security

- **Consent-First Design**: Users must explicitly approve data access via Health Connect
- **Data Encryption**: AES-256-CBC encryption for local storage
- **Key Management**: Automatic key generation and secure storage in Android Keystore
- **No Persistent IDs**: Anonymized UUIDs for experiments
- **Compliant**: Follows Synheart Data Governance Policy
- **Right to Forget**: Users can revoke permissions and delete encrypted data

## 🏗️ Architecture

```
┌─────────────────────────────┐
│   SynheartWear SDK          │
├─────────────────────────────┤
│   Health Connect Adapter    │
│   Samsung Health Adapter    │
├─────────────────────────────┤
│   Normalization Engine      │
│   (standard output schema)  │
├─────────────────────────────┤
│   Local Cache & Storage     │
│   (encrypted, offline)      │
└─────────────────────────────┘
```

## 📋 Health Connect Setup

Add Health Connect permissions to your `AndroidManifest.xml`:

```xml
<manifest>
    <!-- Health Connect permissions -->
    <uses-permission android:name="android.permission.health.READ_HEART_RATE"/>
    <uses-permission android:name="android.permission.health.READ_HEART_RATE_VARIABILITY"/>
    <uses-permission android:name="android.permission.health.READ_STEPS"/>
    <uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED"/>

    <application>
        <!-- Health Connect integration -->
        <activity-alias
            android:name="ViewPermissionUsageActivity"
            android:exported="true"
            android:targetActivity=".MainActivity"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
            <intent-filter>
                <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
            </intent-filter>
        </activity-alias>
    </application>
</manifest>
```

## 🧪 Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate coverage report
./gradlew jacocoTestReport
```

**Note**: If you don't have a Gradle wrapper, initialize it with:
```bash
gradle wrapper --gradle-version 8.0
```

## 🚀 Releasing (for Maintainers)

This project uses [JitPack](https://jitpack.io) for automated builds and releases. When you create a GitHub release, JitPack automatically builds and publishes the library.

### Creating a Release

1. **Update version in `build.gradle.kts`**:
   ```kotlin
   version = "0.2.0"  // Update this
   ```

2. **Commit and push changes**:
   ```bash
   git add .
   git commit -m "chore: bump version to 0.2.0"
   git push origin main
   ```

3. **Create a GitHub release**:
   - Go to the GitHub repository
   - Click "Releases" → "Create a new release"
   - Create a new tag (e.g., `v0.2.0`)
   - Set the release title and description
   - Click "Publish release"

4. **Monitor the build**:
   - Check the [GitHub Actions](https://github.com/synheart-ai/synheart-wear-android/actions) for build status
   - Check [JitPack](https://jitpack.io/#synheart-ai/synheart-wear-android) for the build log

### Release Checklist

- [ ] All tests pass locally (`./gradlew test`)
- [ ] Version number updated in `build.gradle.kts`
- [ ] CHANGELOG updated (if applicable)
- [ ] Documentation updated
- [ ] GitHub release created with appropriate tag
- [ ] JitPack build succeeded

## 🚨 Error Handling

The SDK throws `SynheartWearException` for all errors. Always wrap SDK calls in try-catch blocks:

```kotlin
try {
    val metrics = synheartWear.readMetrics()
    // Process metrics
} catch (e: SynheartWearException) {
    when {
        e.message?.contains("not initialized") == true -> {
            // SDK not initialized
        }
        e.message?.contains("permission") == true -> {
            // Permission denied
        }
        else -> {
            // Other errors
            Log.e(TAG, "SDK error", e)
        }
    }
}
```

## 📝 Production Considerations

### Performance
- The SDK uses Kotlin Coroutines for async operations - ensure your app handles coroutine cancellation properly
- Local caching is enabled by default - monitor cache size and clear old data periodically
- Streaming intervals should be balanced between data freshness and battery consumption

### Security
- Encryption keys are stored in Android Keystore - ensure your app has proper backup/restore handling
- Never log sensitive biometric data in production builds
- Use ProGuard/R8 to obfuscate code in release builds

### Privacy Compliance
- Always request user consent before accessing health data
- Implement proper data retention policies
- Provide users with ability to delete their data (use `purgeAllData()`)
- Follow GDPR, HIPAA, and other applicable regulations

### Health Connect Setup
- Health Connect must be installed on the device (available on Android 14+ or via Play Store)
- Users must grant permissions through Health Connect's permission UI
- Test on devices with Health Connect installed before production deployment

## 🤝 Contributing

We welcome contributions! See the main repository's [Contributing Guidelines](https://github.com/synheart-ai/synheart-wear/blob/main/CONTRIBUTING.md) for details.

## 📄 License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.

## 🔗 Links

- **Main Repository (Source of Truth)**: [synheart-wear](https://github.com/synheart-ai/synheart-wear)
- **Documentation**: [RFC Documentation](https://github.com/synheart-ai/synheart-wear/blob/main/docs/RFC.md)
- **Data Schema**: [Metrics Schema](https://github.com/synheart-ai/synheart-wear/blob/main/schema/metrics.schema.json)
- **Flutter SDK**: [synheart-wear-flutter](https://github.com/synheart-ai/synheart-wear-flutter)
- **iOS SDK**: [synheart-wear-ios](https://github.com/synheart-ai/synheart-wear-ios)
- **CLI Tool**: [synheart-wear-cli](https://github.com/synheart-ai/synheart-wear-cli)
- **Cloud Service**: [synheart-wear-service](https://github.com/synheart-ai/synheart-wear-service)
- **Synheart AI**: [synheart.ai](https://synheart.ai)
- **Issues**: [GitHub Issues](https://github.com/synheart-ai/synheart-wear-android/issues)

## 👥 Authors

- **Israel Goytom** - *Initial work* - [@isrugeek](https://github.com/isrugeek)
- **Synheart AI Team** - *RFC Design & Architecture*

---

**Made with ❤️ by the Synheart AI Team**

*Technology with a heartbeat.*
