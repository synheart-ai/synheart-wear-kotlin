# Synheart Wear - Android SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API 21+](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8%2B-blue.svg)](https://kotlinlang.org)

**Unified wearable SDK for Android** — Stream biometric data from Apple Watch, Fitbit, Garmin, Whoop, and Samsung devices via Health Connect with a single standardized API.

## 🚀 Features

- **📱 Health Connect Integration**: Native Android biometric data access
- **⌚ Multi-Device Support**: Apple Watch, Fitbit, Garmin, Whoop, Samsung Watch
- **🔄 Real-Time Streaming**: Live HR and HRV data streams
- **📊 Unified Schema**: Consistent data format across all devices
- **🔒 Privacy-First**: Consent-based data access with AES-256 encryption
- **💾 Local Storage**: Encrypted offline data persistence
- **⚡ Kotlin Coroutines**: Modern async API with Flow support

## 📦 Installation

### Gradle (Kotlin DSL)

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.synheart:synheart-wear:0.1.0")
}
```

**Note**: Make sure to add the Maven repository where the library is published. If publishing to Maven Central, add:

```kotlin
repositories {
    mavenCentral()
    // or your custom Maven repository
}
```

### Requirements

- **Android SDK**: API 21+ (Android 5.0 Lollipop)
- **Target SDK**: API 34+ (Android 14)
- **Kotlin**: 1.8+
- **Health Connect**: Required for biometric data access (must be installed on device)
- **Gradle**: 7.0+ (for Kotlin DSL support)

## 🎯 Quick Start

### 1. Initialize the SDK

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
| Fitbit | Android | Health Connect | ✅ Ready |
| Garmin | Android | Health Connect | 🔄 In Development |
| Whoop | Android | REST API | 📋 Planned |
| Samsung Watch | Android | Samsung Health SDK | ✅ Ready |
| Google Fit | Android | Health Connect | ✅ Ready |

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

This project is licensed under the MIT License - see the [LICENSE](https://github.com/synheart-ai/synheart-wear/blob/main/LICENSE) file for details.

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
