package ai.synheart.wear.cache

import android.content.Context
import android.util.Log
import ai.synheart.wear.models.WearMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local cache for biometric data with optional encryption.
 *
 * With encryption on, each session is sealed with AES-256-GCM under a key held
 * in the Android Keystore and written as `session_<timestamp>.enc`. If the
 * Keystore can't provide a key (API < 23), sessions are **not cached** rather
 * than written in the clear. With encryption off, sessions are plain JSON
 * (`session_<timestamp>.json`).
 *
 * @param context Android application context
 * @param enableEncryption Whether to encrypt cached data
 */
@OptIn(InternalSerializationApi::class)
class LocalCache internal constructor(
    private val cacheDir: File,
    private val enableEncryption: Boolean,
    private val cipher: CacheCipher?,
    private val warn: (String) -> Unit = {}
) {
    constructor(context: Context, enableEncryption: Boolean) : this(
        cacheDir = File(context.cacheDir, "synheart_wear"),
        enableEncryption = enableEncryption,
        cipher = if (enableEncryption) KeystoreCacheKey.cipherOrNull() else null,
        warn = { Log.w(TAG, it) }
    )

    private val extension = if (enableEncryption) ENCRYPTED_EXT else PLAIN_EXT

    @Volatile private var prepared = false

    /**
     * Store a biometric session
     *
     * @param metrics WearMetrics to store
     */
    suspend fun storeSession(metrics: WearMetrics) = withContext(Dispatchers.IO) {
        prepare()
        val json = JSON.encodeToString(metrics).toByteArray(Charsets.UTF_8)
        val bytes = if (enableEncryption) {
            val c = cipher ?: run {
                warn("Encryption unavailable on this device; session not cached")
                return@withContext
            }
            c.encrypt(json)
        } else {
            json
        }
        // Write to a temp file and rename, so a crash never leaves a
        // half-written record behind.
        val target = File(cacheDir, "$PREFIX${metrics.timestamp}$extension")
        val tmp = File(cacheDir, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /**
     * Get cached sessions within time range, newest first
     *
     * @param startTime Start time in milliseconds
     * @param endTime End time in milliseconds
     * @param limit Maximum number of sessions to return
     * @return List of cached WearMetrics
     */
    suspend fun getSessions(
        startTime: Long,
        endTime: Long,
        limit: Int
    ): List<WearMetrics> = withContext(Dispatchers.IO) {
        prepare()
        if (limit <= 0) return@withContext emptyList()
        if (enableEncryption && cipher == null) return@withContext emptyList()

        sessionFiles()
            .mapNotNull { file -> timestampOf(file)?.let { it to file } }
            .filter { (ts, _) -> ts in startTime..endTime }
            .sortedByDescending { (ts, _) -> ts }
            .asSequence()
            .mapNotNull { (_, file) -> read(file) }
            .take(limit)
            .toList()
    }

    /**
     * Get cache statistics
     *
     * @return Map containing cache statistics
     */
    suspend fun getStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        prepare()
        val files = sessionFiles()
        mapOf(
            "total_sessions" to files.size,
            "total_size_bytes" to files.sumOf { it.length() },
            "oldest_session_ms" to (files.minOfOrNull { it.lastModified() } ?: 0L),
            "newest_session_ms" to (files.maxOfOrNull { it.lastModified() } ?: 0L),
            "encryption_enabled" to enableEncryption
        )
    }

    /**
     * Clear old cached data
     *
     * @param maxAge Maximum age in milliseconds
     */
    suspend fun clearOldData(maxAge: Long) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - maxAge
        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }

    /**
     * Purge all cached data (GDPR compliance)
     */
    suspend fun purgeAll() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Creates the directory and, when encryption is on, deletes any
     * plaintext records left by earlier versions or by running with
     * encryption off. Runs once per instance.
     */
    private fun prepare() {
        if (prepared) return
        synchronized(this) {
            if (prepared) return
            if (!cacheDir.exists()) cacheDir.mkdirs()
            cacheDir.listFiles()?.forEach { file ->
                val stale = file.name.endsWith(".tmp") ||
                    (enableEncryption && file.name.endsWith(PLAIN_EXT))
                if (stale) file.delete()
            }
            prepared = true
        }
    }

    private fun sessionFiles(): List<File> =
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(extension) }
            ?: emptyList()

    private fun timestampOf(file: File): Long? =
        file.name.removePrefix(PREFIX).removeSuffix(extension).toLongOrNull()

    /** Reads one record; an unreadable or tampered record is dropped. */
    private fun read(file: File): WearMetrics? = try {
        val raw = file.readBytes()
        val json = if (enableEncryption) cipher!!.decrypt(raw) else raw
        JSON.decodeFromString<WearMetrics>(json.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        file.delete()
        null
    }

    private companion object {
        const val TAG = "SynheartWear"
        const val PREFIX = "session_"
        const val ENCRYPTED_EXT = ".enc"
        const val PLAIN_EXT = ".json"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
