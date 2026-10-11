package com.zer0state.zzthat.storage

import com.zer0state.zzthat.api.ResolveResponse
import com.zer0state.zzthat.api.generated.models.Resolve
import com.zer0state.zzthat.api.generated.models.ResolveRecord
import com.zer0state.zzthat.api.generated.models.ResolveShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** A clock the FR-041 tests move by hand. */
class FakeClock(private var now: Long = 1_791_072_000_000L) : AppClock {
    override fun nowMillis(): Long = now

    fun advance(seconds: Long) {
        now += seconds * 1000
    }
}

/** FR-041 with a fake clock (tasks.md T060 to T062, SC-6): 60 seconds online, 360 seconds offline, no-store never written, at most 50 entries. */
class ResolveCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val code = "zz-kathy-lost-cat-zz"

    private fun response(canonical: String, cacheable: Boolean = true, creator: String? = null) = ResolveResponse(
        Resolve(
            Resolve.View.`public`,
            canonical,
            ResolveRecord("Lost cat", "Answers on this page.", OffsetDateTime.of(2026, 10, 4, 0, 0, 0, 0, ZoneOffset.UTC)),
            ResolveShare(canonical, null),
            creator,
        ),
        cacheable,
    )

    private fun file() = File(folder.root, "resolve-cache.json")

    @Test
    fun onlineUsesTheCopyFor60Seconds() {
        val clock = FakeClock()
        val cache = ResolveCache(file(), clock)
        cache.store(response(code), code)
        val entry = (cache.lookup(code, online = true) as ResolveCache.Lookup.Fresh).entry
        assertEquals("Lost cat", entry.title)
        clock.advance(59)
        assertEquals(ResolveCache.Lookup.Fresh(entry), cache.lookup(code, online = true))
        clock.advance(1)
        assertEquals("at 60 seconds, online fetches before showing", ResolveCache.Lookup.None, cache.lookup(code, online = true))
    }

    @Test
    fun offlineShowsTheCopyUntil360Seconds() {
        val clock = FakeClock()
        val cache = ResolveCache(file(), clock)
        cache.store(response(code), code)
        clock.advance(60)
        val entry = (cache.lookup(code, online = false) as ResolveCache.Lookup.Offline).entry
        clock.advance(299)
        assertEquals(ResolveCache.Lookup.Offline(entry), cache.lookup(code, online = false))
        clock.advance(1)
        assertEquals("at 360 seconds, not on this device", ResolveCache.Lookup.None, cache.lookup(code, online = false))
        assertEquals("the expired entry is dropped", 0, cache.count)
    }

    @Test
    fun aNoStoreResolveIsNeverWritten() {
        val cache = ResolveCache(file(), FakeClock())
        cache.store(response(code, cacheable = false), code)
        assertEquals(ResolveCache.Lookup.None, cache.lookup(code, online = false))
        assertEquals(0, cache.count)
        assertFalse(file().exists())
    }

    @Test
    fun ownerWritesAndAccountDeletionDropEntries() {
        val cache = ResolveCache(file(), FakeClock())
        cache.store(response(code), code)
        cache.store(response("zz-copper-lantern-sky-zz"), "zz-copper-lantern-sky-zz")
        cache.remove(code)
        assertEquals(ResolveCache.Lookup.None, cache.lookup(code, online = false))
        assertEquals(1, cache.count)
        cache.removeAll()
        assertEquals(0, cache.count)
        assertFalse(file().exists())
    }

    @Test
    fun atMost50EntriesOldestDroppedFirst() {
        val clock = FakeClock()
        val cache = ResolveCache(file(), clock)
        for (index in 0..50) {
            cache.store(response("zz-code-$index-zz"), "zz-code-$index-zz")
            clock.advance(1)
        }
        assertEquals(50, cache.count)
        assertEquals(ResolveCache.Lookup.None, cache.lookup("zz-code-0-zz", online = false))
        assertTrue("newest missing", cache.lookup("zz-code-50-zz", online = false) is ResolveCache.Lookup.Offline)
    }

    @Test
    fun theCopyLivesInOneFileAcrossLaunches() {
        val clock = FakeClock()
        ResolveCache(file(), clock).store(response(code), code)
        clock.advance(30)
        assertTrue(ResolveCache(file(), clock).lookup(code, online = true) is ResolveCache.Lookup.Fresh)
    }

    /** FR-035 and RM-112: the entry keeps the Resolve `creator`, so an offline copy of a blocked creator's code stays blocked. */
    @Test
    fun theCreatorIsKeptAcrossLaunches() {
        val clock = FakeClock()
        ResolveCache(file(), clock).store(response(code, creator = "creator-a"), code)
        ResolveCache(file(), clock).store(response("zz-copper-lantern-sky-zz"), "zz-copper-lantern-sky-zz")
        clock.advance(30)
        val reloaded = ResolveCache(file(), clock)
        assertEquals("creator-a", (reloaded.lookup(code, online = false) as ResolveCache.Lookup.Offline).entry.creator)
        assertEquals(null, (reloaded.lookup("zz-copper-lantern-sky-zz", online = false) as ResolveCache.Lookup.Offline).entry.creator)
    }

    /** A file written before the `creator` field still decodes: a missing creator is null. */
    @Test
    fun anEntryWrittenBeforeTheCreatorFieldStillDecodes() {
        val clock = FakeClock()
        file().writeText(
            """[{"canonical":"$code","title":"Lost cat","body":"Answers on this page.","updatedAtMillis":1791072000000,""" +
                """"shareText":"$code","fetchedAtMillis":${clock.nowMillis()}}]""",
        )
        val entry = (ResolveCache(file(), clock).lookup(code, online = true) as ResolveCache.Lookup.Fresh).entry
        assertEquals("Lost cat", entry.title)
        assertEquals(null, entry.creator)
    }

    @Test
    fun aClockThatMovedBackDropsTheEntry() {
        val clock = FakeClock()
        val cache = ResolveCache(file(), clock)
        cache.store(response(code), code)
        clock.advance(-1)
        assertEquals(ResolveCache.Lookup.None, cache.lookup(code, online = false))
    }

    @Test
    fun theDefaultFileIsInTheCachesFolder() {
        assertEquals(File(folder.root, "resolve-cache.json"), ResolveCache.defaultFile(folder.root))
    }
}
