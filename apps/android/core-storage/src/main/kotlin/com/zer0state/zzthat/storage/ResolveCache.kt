package com.zer0state.zzthat.storage

import com.zer0state.zzthat.api.ResolveResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** The time source FR-041 runs on, so tests can move it. */
fun interface AppClock {
    fun nowMillis(): Long
}

object SystemClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * The one resolve cache (FR-041, FR-049): a small JSON file in the caches
 * folder, keyed by canonical code, at most 50 entries, oldest dropped first.
 * Only a resolve whose `Cache-Control` is the public value is ever written.
 */
class ResolveCache(private val file: File, private val clock: AppClock = SystemClock) {
    @Serializable
    data class Entry(
        val canonical: String,
        val title: String,
        val body: String,
        val updatedAtMillis: Long,
        val shareText: String,
        val fetchedAtMillis: Long,
        /**
         * The Resolve `creator` (FR-035, zzThis RM-112), so a cached copy of a
         * blocked creator's code still shows blocked. Null for a private
         * record, an older server, or an entry written before the field.
         */
        val creator: String? = null,
    )

    sealed interface Lookup {
        /** Online, within 60 seconds of the fetch: use it. */
        data class Fresh(val entry: Entry) : Lookup

        /** Offline, within 360 seconds of the fetch: show it, labeled offline. */
        data class Offline(val entry: Entry) : Lookup

        /** Fetch first (online), or the not-on-this-device state (offline). */
        data object None : Lookup
    }

    private val lock = Any()
    private val entries: MutableMap<String, Entry> = load()

    /** Keeps a resolve under the canonical code the app asked for. A response that is not cacheable is never written (FR-041, FR-023). */
    fun store(response: ResolveResponse, canonical: String) {
        if (!response.cacheable) return
        val record = response.record
        val entry = Entry(
            canonical = canonical,
            title = record.record.title,
            body = record.record.body,
            updatedAtMillis = record.record.updatedAt.toInstant().toEpochMilli(),
            shareText = record.share.text,
            fetchedAtMillis = clock.nowMillis(),
            creator = record.creator,
        )
        synchronized(lock) {
            entries[canonical] = entry
            while (entries.size > CAPACITY) {
                val oldest = entries.values.minByOrNull { it.fetchedAtMillis } ?: break
                entries.remove(oldest.canonical)
            }
            persist()
        }
    }

    fun lookup(canonical: String, online: Boolean): Lookup = synchronized(lock) {
        val entry = entries[canonical] ?: return Lookup.None
        val age = clock.nowMillis() - entry.fetchedAtMillis
        if (age >= OFFLINE_LIFETIME_MILLIS || age < 0) {
            entries.remove(canonical)
            persist()
            return Lookup.None
        }
        if (online) {
            if (age < ONLINE_LIFETIME_MILLIS) Lookup.Fresh(entry) else Lookup.None
        } else {
            Lookup.Offline(entry)
        }
    }

    /** After a 2xx from revoke, re-roll, or a record version for this code, and after a not-found answer. */
    fun remove(canonical: String) {
        synchronized(lock) {
            if (entries.remove(canonical) != null) persist()
        }
    }

    /** `DELETE /v1/me` clears every entry. */
    fun removeAll() {
        synchronized(lock) {
            entries.clear()
            file.delete()
        }
    }

    val count: Int get() = synchronized(lock) { entries.size }

    private fun load(): MutableMap<String, Entry> {
        val stored = try {
            if (file.exists()) JSON.decodeFromString(LIST, file.readText(Charsets.UTF_8)) else emptyList()
        } catch (error: Exception) {
            emptyList()
        }
        val map = linkedMapOf<String, Entry>()
        for (entry in stored) {
            val known = map[entry.canonical]
            if (known == null || entry.fetchedAtMillis > known.fetchedAtMillis) map[entry.canonical] = entry
        }
        return map
    }

    private fun persist() {
        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(JSON.encodeToString(LIST, entries.values.sortedBy { it.fetchedAtMillis }), Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        } catch (error: IOException) {
            // A cache that cannot be written is only a cache: the next resolve fetches again.
        }
    }

    companion object {
        const val ONLINE_LIFETIME_MILLIS = 60_000L
        const val OFFLINE_LIFETIME_MILLIS = 360_000L
        const val CAPACITY = 50
        const val FILE_NAME = "resolve-cache.json"

        /** The default file, in the app's caches folder. */
        fun defaultFile(cacheDir: File): File = File(cacheDir, FILE_NAME)

        private val JSON = Json { ignoreUnknownKeys = true }
        private val LIST = ListSerializer(Entry.serializer())
    }
}
