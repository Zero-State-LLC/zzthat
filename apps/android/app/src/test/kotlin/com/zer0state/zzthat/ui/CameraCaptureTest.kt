package com.zer0state.zzthat.ui

import com.zer0state.zzthat.storage.PhotoScratch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CameraCaptureTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun cancelledCameraCaptureDeletesItsScratchPhoto() {
        val photos = PhotoScratch(File(folder.root, PhotoScratch.FOLDER))
        val pending = photos.begin(byteArrayOf(1, 2, 3))

        assertNull(savedCameraFile(saved = false, file = pending, photos = photos))
        assertFalse(pending.exists())
        assertTrue(photos.storedFiles.isEmpty())
    }

    @Test
    fun successfulCameraCaptureKeepsItsScratchPhotoForTheScan() {
        val photos = PhotoScratch(File(folder.root, PhotoScratch.FOLDER))
        val pending = photos.begin(byteArrayOf(1, 2, 3))

        assertSame(pending, savedCameraFile(saved = true, file = pending, photos = photos))
        assertTrue(pending.exists())
        photos.end()
        assertFalse(pending.exists())
    }
}
