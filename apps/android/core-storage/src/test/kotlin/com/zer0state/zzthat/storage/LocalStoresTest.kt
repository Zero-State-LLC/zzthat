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
import org.junit.Assert.assertFalse
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

    /** FR-035: hidden codes stay on the device until unhidden or cleared, beside the other local values. */
    @Test
    fun hiddenCodesAreKeptUntilUnhiddenOrCleared() = runBlocking {
        val store = dataStore()
        val hidden = HiddenStore(store)
        val last = LastCanonicalStore(store)
        last.set("zz-kathy-lost-cat-zz")
        assertFalse(hidden.isHidden("zz-kathy-lost-cat-zz"))
        hidden.hide("zz-kathy-lost-cat-zz")
        hidden.hide("zz-copper-lantern-sky-zz")
        hidden.hide("zz-kathy-lost-cat-zz")
        assertTrue(hidden.isHidden("zz-kathy-lost-cat-zz"))
        assertTrue(HiddenStore(store).isHidden("zz-copper-lantern-sky-zz"))
        hidden.unhide("zz-kathy-lost-cat-zz")
        assertFalse(hidden.isHidden("zz-kathy-lost-cat-zz"))
        assertTrue("unhiding one code keeps the others", hidden.isHidden("zz-copper-lantern-sky-zz"))
        hidden.clear()
        assertFalse(hidden.isHidden("zz-copper-lantern-sky-zz"))
        assertEquals("clearing the list keeps the other local values", "zz-kathy-lost-cat-zz", last.value())
    }

    /** FR-035 and RM-112: blocked creators are a second list beside the hidden codes, and clear() empties both. */
    @Test
    fun blockedCreatorsAreKeptUntilUnblockedOrCleared() = runBlocking {
        val store = dataStore()
        val hidden = HiddenStore(store)
        assertFalse(hidden.isBlocked("creator-a"))
        hidden.block("creator-a")
        hidden.block("creator-b")
        hidden.hide("zz-kathy-lost-cat-zz")
        assertTrue(HiddenStore(store).isBlocked("creator-a"))
        assertFalse("a creator reference is not a hidden code", hidden.isHidden("creator-a"))
        assertFalse("a hidden code is not a blocked creator", hidden.isBlocked("zz-kathy-lost-cat-zz"))
        hidden.unblock("creator-a")
        assertFalse(hidden.isBlocked("creator-a"))
        assertTrue("unblocking one creator keeps the others", hidden.isBlocked("creator-b"))
        hidden.clear()
        assertFalse(hidden.isBlocked("creator-b"))
        assertFalse(hidden.isHidden("zz-kathy-lost-cat-zz"))
    }

    /** FR-050 and RM-115: Saved keeps canonical codes, newest first, until removed or cleared. */
    @Test
    fun savedCodesAreKeptNewestFirstUntilRemovedOrCleared() = runBlocking {
        val store = dataStore()
        val saved = SavedStore(store)
        val last = LastCanonicalStore(store)
        last.set("zz-kathy-lost-cat-zz")
        assertEquals(emptyList<String>(), saved.list())
        saved.save("zz-kathy-lost-cat-zz")
        saved.save("zz-copper-lantern-sky-zz")
        saved.save("zz-b2-4-zz")
        assertEquals(listOf("zz-b2-4-zz", "zz-copper-lantern-sky-zz", "zz-kathy-lost-cat-zz"), saved.list())
        assertEquals("the list is on disk", saved.list(), SavedStore(store).list())
        saved.save("zz-kathy-lost-cat-zz")
        assertEquals("saving again moves the code to the top, once", listOf("zz-kathy-lost-cat-zz", "zz-b2-4-zz", "zz-copper-lantern-sky-zz"), saved.list())
        assertTrue(saved.isSaved("zz-b2-4-zz"))
        saved.unsave("zz-b2-4-zz")
        assertFalse(saved.isSaved("zz-b2-4-zz"))
        assertEquals(listOf("zz-kathy-lost-cat-zz", "zz-copper-lantern-sky-zz"), saved.list())
        saved.clear()
        assertEquals(emptyList<String>(), saved.list())
        assertEquals("clearing Saved keeps the other local values", "zz-kathy-lost-cat-zz", last.value())
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
