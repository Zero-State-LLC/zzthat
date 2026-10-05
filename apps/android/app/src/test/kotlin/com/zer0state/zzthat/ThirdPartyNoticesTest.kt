package com.zer0state.zzthat

import com.zer0state.zzthat.account.LICENSE_RUN_LINES
import com.zer0state.zzthat.account.licenseRuns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The third-party notices the build generates into the assets (tasks.md
 * T087, Danny on #43): every library on the release runtime classpath with
 * its license, each license given once in full or by its address, the
 * components Google's libraries list for the code built into them, and the
 * Material icon the app copies as source. The build itself fails on a
 * library or component with no license it can find.
 */
class ThirdPartyNoticesTest {
    private val rule = "=".repeat(64)
    private val embeddedRule = "-".repeat(64)
    private val notices = File(System.getProperty("zz.notices")).readText()
    private val releaseLibraries = File(System.getProperty("zz.releaseLibraries")).readLines().filter { it.isNotBlank() }
    private val embedded = File(System.getProperty("zz.releaseEmbedded"))
    private val app = File(System.getProperty("zz.appSrc")).parentFile
    private val camera = "github.com/google/material-design-icons src/image/photo_camera/materialicons/24px.svg"

    /** The list before the first rule: each name, then its licenses indented by four spaces. */
    private val entries: List<Pair<String, List<String>>> = buildList {
        for (line in notices.substringBefore("\n$rule\n").lines().filter { it.isNotEmpty() }) {
            if (line.startsWith("    ")) {
                val (name, licenses) = removeAt(lastIndex)
                add(name to licenses + line.trim())
            } else {
                add(line to emptyList())
            }
        }
    }

    private fun String.occurrences(part: String): Int {
        var count = 0
        var index = indexOf(part)
        while (index >= 0) {
            count++
            index = indexOf(part, index + part.length)
        }
        return count
    }

    /** How often [part] stands whole: followed by the end of the file or by the next rule, not by more text. */
    private fun String.wholeParts(part: String): Int {
        var count = 0
        var index = indexOf(part)
        while (index >= 0) {
            val end = index + part.length
            if (end == length || startsWith("\n$rule\n", end) || startsWith("\n$embeddedRule\n", end)) count++
            index = indexOf(part, index + 1)
        }
        return count
    }

    @Test
    fun everyLibraryOnTheReleaseClasspathIsListedWithItsLicense() {
        assertTrue("${releaseLibraries.size} libraries on the release classpath", releaseLibraries.size > 100)
        assertEquals(releaseLibraries.sorted() + camera, entries.map { it.first })
        for ((name, licenses) in entries) assertTrue("$name has no license", licenses.isNotEmpty())
    }

    @Test
    fun eachLicenseIsGivenOnceInFullOrByItsAddress() {
        val used = entries.flatMap { it.second }.toSortedSet()
        assertTrue("Apache-2.0" in used)
        for (license in used) {
            val head = "\n$rule\n$license\n"
            assertEquals(license, 1, notices.occurrences(head))
            val text = File(app, "licenses/$license.txt")
            if (text.exists()) {
                assertTrue(license, notices.contains("$head\n${text.readText().trimEnd()}\n"))
            } else {
                // Terms published only as a web page: the address, and nothing more.
                val rest = notices.substringAfter(head)
                assertTrue("$license has neither its text nor its address", rest.substringBefore('\n').matches(Regex("https://\\S+")))
                val next = rest.substringAfter('\n')
                assertTrue(license, next.isEmpty() || next.startsWith("\n$rule\n") || next.startsWith("\n$embeddedRule\n"))
            }
        }
        assertTrue(File(app, "licenses/Apache-2.0.txt").readText().contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        // Every other part under a rule is a NOTICE file that one of the listed libraries carries.
        val heads = notices.split("\n$rule\n").drop(1).map { it.substringBefore('\n') }
        for (head in heads.filterNot { it in used }) {
            assertTrue(head, releaseLibraries.any { head.startsWith("$it ") })
        }
    }

    /**
     * Google's libraries list the code built into them in
     * third_party_licenses.json, which places each component's text in
     * third_party_licenses.txt by byte offset and length. Every component is
     * listed over its text, and each text is given once, after every
     * component that uses it.
     */
    @Test
    fun theEmbeddedLicensesAreGivenOncePerText() {
        val tables = embedded.walkTopDown().filter { it.name == "third_party_licenses.json" }.toList()
        assertTrue("${tables.size} libraries carry third_party_licenses.json", tables.size >= 20)
        val components = mutableMapOf<String, MutableSet<String>>()
        for (table in tables) {
            val bytes = File(table.parentFile, "third_party_licenses.txt").readBytes()
            for ((component, place) in Json.parseToJsonElement(table.readText()).jsonObject) {
                val start = place.jsonObject.getValue("start").jsonPrimitive.int
                val length = place.jsonObject.getValue("length").jsonPrimitive.int
                val text = String(bytes, start, length, Charsets.UTF_8).trimEnd()
                assertTrue("$component in ${table.parentFile}", text.isNotBlank())
                components.getOrPut(text) { sortedSetOf() } += component.trim()
            }
        }
        assertTrue("${components.size} embedded texts", components.size > 100)
        for ((text, names) in components) {
            val section = "\n$embeddedRule\n" + names.joinToString("\n") + "\n\n" + text + "\n"
            // One Kotlin text begins with another, so a section counts only where it ends.
            assertEquals(names.first(), 1, notices.wholeParts(section))
        }
        assertEquals(components.size, notices.occurrences("\n$embeddedRule\n"))
    }

    /** The Scan tab's camera icon is copied source, not an artifact, so it is listed by its upstream path (Danny, #43). */
    @Test
    fun theCopiedCameraIconIsListed() {
        assertEquals(listOf(camera to listOf("Apache-2.0")), entries.filter { it.first == camera })
        val source = File(app, "src/main/kotlin/com/zer0state/zzthat/ui/PhotoCameraIcon.kt").readText()
        assertTrue(source.contains("Licensed under the Apache License, Version 2.0"))
        assertTrue(source.contains("src/image/photo_camera/materialicons/24px.svg"))
    }

    /** The Licenses screen lays each text out in runs of lines, and the runs together are the text as given. */
    @Test
    fun theScreenShowsTheNoticesAsGiven() {
        val runs = licenseRuns(notices)
        assertEquals(notices, runs.joinToString("\n"))
        assertTrue("${runs.size} runs", runs.size > 100)
        for (run in runs) assertTrue(run.count { it == '\n' } <= LICENSE_RUN_LINES)
        // NOTICE and OFL.txt stay one panel each.
        val pinned = File(System.getProperty("zz.apps"), "android/core-design/src/main/assets/licenses")
        for (name in listOf("NOTICE", "OFL.txt")) assertEquals(name, 1, licenseRuns(File(pinned, name).readText()).size)
        assertEquals(listOf(""), licenseRuns(""))
        assertEquals(listOf("a\n"), licenseRuns("a\n"))
    }
}
