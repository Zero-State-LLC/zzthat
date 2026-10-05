package com.zer0state.zzthat.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** runtime.md, Auth step 6, on a device: AES-GCM with a key that stays in the Android Keystore. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val alias = "zzthat.test-refresh-token"

    @Test
    fun aTokenRoundTripsAndTheBlobIsNotTheToken() {
        val cipher = KeystoreTokenCipher(alias)
        val token = "refresh-secret-1".toByteArray()
        val blob = cipher.encrypt(token)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("refresh-secret-1"))
        assertArrayEquals(token, cipher.decrypt(blob))
    }

    /** Without its key (a restore to another device), the blob does not decrypt. */
    @Test
    fun aBlobWithoutItsKeyDoesNotDecrypt() {
        val blob = KeystoreTokenCipher(alias).encrypt("refresh-secret-2".toByteArray())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        assertThrows(Exception::class.java) { KeystoreTokenCipher(alias).decrypt(blob) }
    }
}
