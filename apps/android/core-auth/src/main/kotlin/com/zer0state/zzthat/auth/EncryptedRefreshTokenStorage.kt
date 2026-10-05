package com.zer0state.zzthat.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zer0state.zzthat.api.RefreshTokenStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the refresh token at rest. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws when the blob was not made by this cipher's key. */
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-GCM with a key held in the Android Keystore (runtime.md, Auth step 6).
 * The key never leaves the Keystore. A blob is the 12-byte IV, then the
 * ciphertext and its tag.
 */
class KeystoreTokenCipher(private val alias: String = ALIAS) : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "blob too short" }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(alias, null) as? SecretKey ?: throw IllegalStateException("no key")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val ALIAS = "zzthat.refresh-token"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * The refresh token, encrypted by [cipher] and kept in DataStore (runtime.md,
 * Auth step 6). Not `EncryptedSharedPreferences`, which is deprecated. Any
 * decrypt failure counts as signed out, and the stored blob is deleted.
 * Calls block, so they run off the main thread (OkHttp's, or Dispatchers.IO).
 */
class EncryptedRefreshTokenStorage(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : RefreshTokenStorage {
    private val lock = Any()
    private var loaded = false
    private var cached: String? = null

    override fun load(): String? = synchronized(lock) {
        if (loaded) return cached
        val blob = runBlocking { store.data.first()[KEY] }
        cached = blob?.let { encoded ->
            try {
                String(cipher.decrypt(Base64.getDecoder().decode(encoded)), Charsets.UTF_8)
            } catch (error: Exception) {
                runBlocking { store.edit { it.remove(KEY) } }
                null
            }
        }
        loaded = true
        cached
    }

    override fun save(token: String) {
        synchronized(lock) {
            val blob = Base64.getEncoder().encodeToString(cipher.encrypt(token.toByteArray(Charsets.UTF_8)))
            runBlocking { store.edit { it[KEY] = blob } }
            cached = token
            loaded = true
        }
    }

    override fun delete() {
        synchronized(lock) {
            runBlocking { store.edit { it.remove(KEY) } }
            cached = null
            loaded = true
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("session.refresh_token")
    }
}
