package com.zer0state.zzthat.storage

import java.io.File
import java.util.UUID

/**
 * The photo of one scan or write check, in the caches folder only while it
 * runs, and deleted when it ends (FR-004, T063).
 */
class PhotoScratch(private val directory: File) {
    private val lock = Any()
    private var current: File? = null

    /** A new empty file for the camera to write the photo into, replacing any earlier one. */
    fun begin(): File = synchronized(lock) {
        directory.mkdirs()
        current?.delete()
        File(directory, "${UUID.randomUUID()}.jpg").also {
            it.createNewFile()
            current = it
        }
    }

    /** Keeps these photo bytes for the scan in progress, replacing any earlier photo. */
    fun begin(bytes: ByteArray): File = begin().also { it.writeBytes(bytes) }

    val currentFile: File? get() = synchronized(lock) { current }

    /** The scan or write check ended: delete its photo. */
    fun end() {
        synchronized(lock) {
            current?.delete()
            current = null
        }
    }

    /** At launch: delete anything a crash left behind. */
    fun purge() {
        synchronized(lock) {
            directory.deleteRecursively()
            current = null
        }
    }

    val storedFiles: List<File> get() = directory.listFiles()?.toList().orEmpty()

    companion object {
        const val FOLDER = "scan-photos"

        /** The default folder, in the app's caches folder, which the FileProvider shares with the camera. */
        fun defaultDirectory(cacheDir: File): File = File(cacheDir, FOLDER)
    }
}
