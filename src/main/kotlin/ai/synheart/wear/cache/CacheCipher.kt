package ai.synheart.wear.cache

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals and opens cache records. */
internal interface CacheCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(record: ByteArray): ByteArray
}

/**
 * AES-256-GCM over a caller-supplied key.
 *
 * Record layout: `[version:1][iv:12][ciphertext + 16-byte tag]`. Every record
 * gets a fresh IV from the cipher, so no IV is ever reused under one key, and
 * the tag makes a tampered or truncated record fail to open instead of
 * decrypting to garbage.
 */
internal class AesGcmCacheCipher(private val key: () -> SecretKey) : CacheCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV passed: the provider generates a random one (and the Android
        // Keystore refuses a caller-chosen IV by default).
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected GCM IV length ${iv.size}" }
        val sealed = cipher.doFinal(plaintext)
        return byteArrayOf(VERSION) + iv + sealed
    }

    override fun decrypt(record: ByteArray): ByteArray {
        require(record.size > 1 + IV_BYTES + TAG_BYTES) { "Record too short" }
        require(record[0] == VERSION) { "Unknown record version ${record[0]}" }
        val iv = record.copyOfRange(1, 1 + IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BYTES * 8, iv))
        return cipher.doFinal(record, 1 + IV_BYTES, record.size - 1 - IV_BYTES)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}

/**
 * The cache key lives in the Android Keystore: it never leaves secure
 * hardware (where the device has it) and is not part of app backups.
 */
internal object KeystoreCacheKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "ai.synheart.wear.cache.v1"

    /**
     * Returns a cipher bound to the Keystore key, or `null` when the Keystore
     * can't hold an AES key (API < 23) or fails to produce one.
     */
    fun cipherOrNull(): CacheCipher? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return try {
            val key = getOrCreate()
            AesGcmCacheCipher { key }
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreate(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
