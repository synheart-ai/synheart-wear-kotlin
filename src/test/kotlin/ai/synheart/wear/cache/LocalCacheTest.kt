package ai.synheart.wear.cache

import ai.synheart.wear.models.WearMetrics
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class LocalCacheTest {

    private lateinit var dir: File
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCacheCipher { key }

    private fun metrics(ts: Long, hr: Double = 60.0) = WearMetrics(
        timestamp = ts,
        deviceId = "device-a",
        source = "health_connect",
        metrics = mapOf("hr" to hr, "hrv_rmssd" to 42.5),
        meta = mapOf("note" to "x"),
        rrIntervals = listOf(800.0, 810.0)
    )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("wear-cache").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun encryptedRoundTrip() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        val original = metrics(1_000)
        cache.storeSession(original)

        assertEquals(listOf(original), cache.getSessions(0, 2_000, 10))
    }

    @Test
    fun encryptedRecordsDoNotContainPlaintext() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        cache.storeSession(metrics(1_000))

        val files = dir.listFiles()!!.toList()
        assertEquals(1, files.size)
        assertTrue(files[0].name.endsWith(".enc"))
        val text = files[0].readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(text.contains("health_connect"))
        assertFalse(text.contains("device-a"))
        assertFalse(text.contains("hrv_rmssd"))
    }

    @Test
    fun identicalPlaintextsEncryptDifferently() {
        val plain = "same".toByteArray()
        val a = cipher.encrypt(plain)
        val b = cipher.encrypt(plain)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain, cipher.decrypt(a))
        assertArrayEquals(plain, cipher.decrypt(b))
    }

    @Test
    fun tamperedRecordIsDropped() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        cache.storeSession(metrics(1_000))
        val file = dir.listFiles()!!.single()
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)

        assertTrue(cache.getSessions(0, 2_000, 10).isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun recordFromAnotherKeyIsDropped() = runBlocking {
        LocalCache(dir, enableEncryption = true, cipher = cipher).storeSession(metrics(1_000))
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val other = LocalCache(dir, enableEncryption = true, cipher = AesGcmCacheCipher { otherKey })

        assertTrue(other.getSessions(0, 2_000, 10).isEmpty())
    }

    @Test
    fun nothingIsWrittenWhenEncryptionIsUnavailable() = runBlocking {
        val warnings = mutableListOf<String>()
        val cache = LocalCache(dir, enableEncryption = true, cipher = null, warn = { warnings += it })
        cache.storeSession(metrics(1_000))

        assertTrue(dir.listFiles()!!.isEmpty())
        assertEquals(1, warnings.size)
        assertTrue(cache.getSessions(0, 2_000, 10).isEmpty())
    }

    @Test
    fun plaintextLeftoversAreDeletedWhenEncryptionIsOn() = runBlocking {
        dir.mkdirs()
        val legacy = File(dir, "session_500.json").apply { writeText("WearMetrics(timestamp=500, ...)") }
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        cache.storeSession(metrics(1_000))

        assertFalse(legacy.exists())
    }

    @Test
    fun unencryptedModeWritesReadableJson() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = false, cipher = null)
        val original = metrics(1_000)
        cache.storeSession(original)

        val file = dir.listFiles()!!.single()
        assertTrue(file.name.endsWith(".json"))
        assertTrue(file.readText().contains("health_connect"))
        assertEquals(listOf(original), cache.getSessions(0, 2_000, 10))
    }

    @Test
    fun sessionsAreFilteredSortedNewestFirstAndLimited() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        listOf(1_000L, 5_000L, 3_000L, 9_000L, 7_000L).forEach { cache.storeSession(metrics(it)) }

        val result = cache.getSessions(startTime = 2_000, endTime = 8_000, limit = 2)
        assertEquals(listOf(7_000L, 5_000L), result.map { it.timestamp })
    }

    @Test
    fun statsCountOnlySessionRecords() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        cache.storeSession(metrics(1_000))
        cache.storeSession(metrics(2_000))

        val stats = cache.getStats()
        assertEquals(2, stats["total_sessions"])
        assertEquals(true, stats["encryption_enabled"])
    }

    @Test
    fun purgeRemovesEverything() = runBlocking {
        val cache = LocalCache(dir, enableEncryption = true, cipher = cipher)
        cache.storeSession(metrics(1_000))
        cache.purgeAll()

        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
