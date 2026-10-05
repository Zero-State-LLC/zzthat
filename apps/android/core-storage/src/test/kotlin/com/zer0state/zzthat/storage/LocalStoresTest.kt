package com.zer0state.zzthat.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalStoresTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "local.preferences_pb") }

    @After
    fun tearDown() = scope.cancel()

    /** FR-024: the draft holds a title and a body, and only text that is not blank. */
    @Test
    fun theDraftKeepsTitleAndBodyUntilDiscarded() = runBlocking {
        val drafts = DraftStore(dataStore())
        assertNull(drafts.draft())
        drafts.save("Lost cat", "Answers on this page.")
        assertEquals(DraftStore.Draft("Lost cat", "Answers on this page."), drafts.draft())
        drafts.save("  ", "\n")
        assertNull("blank text is no draft", drafts.draft())
        drafts.save("Lost cat", "")
        drafts.discard()
        assertNull(drafts.draft())
    }

    @Test
    fun theLastCanonicalAndTheProviderAreKeptUntilCleared() = runBlocking {
        val store = dataStore()
        val last = LastCanonicalStore(store)
        val provider = ProviderStore(store)
        last.set("zz-kathy-lost-cat-zz")
        provider.set("google")
        assertEquals("zz-kathy-lost-cat-zz", last.value())
        assertEquals("google", provider.value())
        last.set(null)
        provider.set(null)
        assertNull(last.value())
        assertNull(provider.value())
    }

    /** FR-004 and T063: the photo lives only while the scan runs. */
    @Test
    fun thePhotoIsDeletedWhenTheScanEnds() {
        val scratch = PhotoScratch(File(folder.root, PhotoScratch.FOLDER))
        val first = scratch.begin(byteArrayOf(1, 2, 3))
        assertTrue(first.exists())
        val second = scratch.begin(byteArrayOf(4))
        assertTrue("a new scan replaces the earlier photo", !first.exists() && second.exists())
        assertEquals(listOf(second), scratch.storedFiles)
        scratch.end()
        assertTrue(scratch.storedFiles.isEmpty())
        File(folder.root, PhotoScratch.FOLDER + "/left-over.jpg").writeBytes(byteArrayOf(9))
        scratch.purge()
        assertTrue("launch deletes what a crash left behind", scratch.storedFiles.isEmpty())
    }
}
