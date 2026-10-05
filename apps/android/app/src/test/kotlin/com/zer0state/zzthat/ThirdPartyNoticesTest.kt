package com.zer0state.zzthat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The third-party notices the build generates into the assets (tasks.md
 * T087, Danny on #43): every library on the release runtime classpath with
 * its license, each license given once in full or by its address, and the
 * Material icon the app copies as source. The build itself fails on a library
 * with no license it can find.
 */
class ThirdPartyNoticesTest {
    private val rule = "=".repeat(64)
    private val notices = File(System.getProperty("zz.notices")).readText()
    private val releaseLibraries = File(System.getProperty("zz.releaseLibraries")).readLines().filter { it.isNotBlank() }
    private val app = File(System.getProperty("zz.appSrc")).parentFile
    private val camera = "github.com/google/material-design-icons src/image/photo_camera/materialicons/24px.svg"

    private val parts = notices.split("\n$rule\n")

    /** The list before the first rule: each name, then its licenses indented by four spaces. */
    private val entries: List<Pair<String, List<String>>> = buildList {
        for (line in parts.first().lines().filter { it.isNotEmpty() }) {
            if (line.startsWith("    ")) {
                val (name, licenses) = removeAt(lastIndex)
                add(name to licenses + line.trim())
            } else {
                add(line to emptyList())
            }
        }
    }

    /** Each part after a rule: its first line, then the rest. */
    private val sections: List<Pair<String, String>> = parts.drop(1).map { it.substringBefore('\n') to it.substringAfter('\n') }

    @Test
    fun everyLibraryOnTheReleaseClasspathIsListedWithItsLicense() {
        assertTrue("${releaseLibraries.size} libraries on the release classpath", releaseLibraries.size > 100)
        assertEquals(releaseLibraries.sorted() + camera, entries.map { it.first })
        for ((name, licenses) in entries) assertTrue("$name has no license", licenses.isNotEmpty())
    }

    @Test
    fun eachLicenseIsGivenOnceInFullOrByItsAddress() {
        val used = entries.flatMap { it.second }.toSortedSet()
        val headers = sections.map { it.first }
        for (license in used) assertEquals(license, 1, headers.count { it == license })
        for ((header, body) in sections.filter { it.first in used }) {
            val text = File(app, "licenses/$header.txt")
            if (text.exists()) {
                assertEquals(header, "\n" + text.readText().trimEnd() + "\n", body)
            } else {
                assertTrue("$header has neither its text nor its address", body.matches(Regex("https://\\S+\n")))
            }
        }
        assertTrue("Apache-2.0" in used)
        assertTrue(File(app, "licenses/Apache-2.0.txt").readText().contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        // Every other part is a NOTICE file that one of the listed libraries carries.
        for (header in headers.filterNot { it in used }) {
            assertTrue(header, releaseLibraries.any { header.startsWith("$it ") })
        }
    }

    /** The Scan tab's camera icon is copied source, not an artifact, so it is listed by its upstream path (Danny, #43). */
    @Test
    fun theCopiedCameraIconIsListed() {
        assertEquals(listOf(camera to listOf("Apache-2.0")), entries.filter { it.first == camera })
        val source = File(app, "src/main/kotlin/com/zer0state/zzthat/ui/PhotoCameraIcon.kt").readText()
        assertTrue(source.contains("Licensed under the Apache License, Version 2.0"))
        assertTrue(source.contains("src/image/photo_camera/materialicons/24px.svg"))
    }
}
