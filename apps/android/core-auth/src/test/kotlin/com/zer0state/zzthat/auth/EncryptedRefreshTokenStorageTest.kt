package com.zer0state.zzthat.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The same AES-GCM blob layout as the Keystore cipher, with a key in memory, for JVM tests. */
class SoftwareCipher(private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()) : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}

/** runtime.md, Auth step 6: the refresh token at rest, and a decrypt failure counted as signed out. */
class EncryptedRefreshTokenStorageTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file get() = File(folder.root, "session.preferences_pb")
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { file } }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun theTokenIsEncryptedAtRestAndReadBack() {
        val cipher = SoftwareCipher()
        EncryptedRefreshTokenStorage(dataStore, cipher).save("refresh-secret-1")
        assertFalse("the file holds no plaintext token", String(file.readBytes(), Charsets.ISO_8859_1).contains("refresh-secret-1"))
        assertEquals("refresh-secret-1", EncryptedRefreshTokenStorage(dataStore, cipher).load())
    }

    /** A blob made with another key (a restore to another device) counts as signed out, and the blob is deleted. */
    @Test
    fun aDecryptFailureIsSignedOutAndDeletesTheBlob() {
        EncryptedRefreshTokenStorage(dataStore, SoftwareCipher()).save("refresh-secret-1")
        val otherDevice = EncryptedRefreshTokenStorage(dataStore, SoftwareCipher())
        assertNull(otherDevice.load())
        assertNull("the blob is gone", EncryptedRefreshTokenStorage(dataStore, SoftwareCipher()).load())
    }

    @Test
    fun deleteRemovesTheToken() {
        val cipher = SoftwareCipher()
        val storage = EncryptedRefreshTokenStorage(dataStore, cipher)
        storage.save("refresh-secret-1")
        storage.delete()
        assertNull(storage.load())
        assertNull(EncryptedRefreshTokenStorage(dataStore, cipher).load())
    }
}
