package com.zer0state.zzthat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zer0state.zzthat.account.licenseRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Demo build end to end, with no network, as iOS DemoFlowTests runs it:
 * the tabs, the fixture photo read on the device by ML Kit, typed entry,
 * demo sign-in, mint and re-roll, write check, and the owner list. It checks
 * that every route opens the screen it names.
 */
@RunWith(AndroidJUnit4::class)
class DemoFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String, timeout: Long = 10_000) = rule.waitUntil(timeout) { exists(tag) }

    private fun tap(tag: String, timeout: Long = 10_000) {
        waitFor(tag, timeout)
        rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollToIfScrollable().performClick()
        rule.waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToIfScrollable() = try {
        performScrollTo()
    } catch (error: AssertionError) {
        this
    } catch (error: IllegalStateException) {
        this
    }

    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun code(tag: String): String = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()

    /**
     * The fixture photo, read on the device. One candidate in Accept opens at
     * once; ML Kit's line confidence for it can sit just under 0.80, and then
     * the person picks the one row and keeps the reading, the Pick and
     * Clarify path of the same flow.
     */
    private fun openFixturePhoto() {
        tap("scan.take_photo")
        // The first ML Kit read on a fresh emulator loads the model.
        rule.waitUntil(60_000) { exists("record.title") || exists("scan.candidate.1") }
        if (!exists("record.title")) {
            tap("scan.candidate.1")
            tap("clarify.as_written")
        }
        waitFor("record.title")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scanTypeCreateAndMyCodesInDemo() {
        waitFor("demo.badge")

        // The photo picker is named scan.choose_photo (Danny, #43 A1), apart from the camera button.
        val strings = rule.activity.resources
        rule.onNodeWithTag("scan.photo_picker").assertContentDescriptionEquals(strings.getString(R.string.scan_choose_photo))
        rule.onNodeWithTag("scan.take_photo").assertTextEquals(strings.getString(R.string.scan_take_photo))

        // Scan the fixture photo on the device, then report it.
        openFixturePhoto()
        tap("resolve.report")
        tap("report.reason.spam")
        tap("report.send")
        tap("report.done")
        waitFor("record.title")
        back()

        // A near-word asks the person: zz-coper-lantern-sky-zz offers copper.
        tap("scan.type_instead")
        waitFor("type.field")
        rule.onNodeWithTag("type.field").performTextClearance()
        rule.onNodeWithTag("type.field").performTextInput("zz-coper-lantern-sky-zz")
        tap("type.open")
        tap("clarify.candidate.copper")
        waitFor("record.title")
        back()
        back()
        back()

        // Type a demo code and open it.
        tap("scan.type_instead")
        tap("type.open")
        waitFor("record.title")

        // Create signs in with a demo button, mints a demo code, and re-rolls it.
        tap("tab.create")
        tap("create.sign_in")
        tap("signin.google")
        waitFor("create.title")
        rule.onNodeWithTag("create.title").performTextInput("Lost cat")
        tap("create.submit")
        waitFor("minted.code")
        val first = code("minted.code")
        tap("minted.reroll")
        rule.waitUntil(10_000) { code("minted.code") != first }
        assertNotEquals(first, code("minted.code"))
        tap("minted.write_check")
        tap("scan.take_photo")
        rule.waitUntil(60_000) { exists("message.writecheck_match") || exists("scan.candidate.1") }
        if (!exists("message.writecheck_match")) tap("scan.candidate.1")
        waitFor("message.writecheck_match")

        // My codes lists the new code; its detail opens.
        tap("tab.codes")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("codes.row.demo-minted-2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        tap("codes.row.demo-minted-2")
        waitFor("detail.share")
        back()

        // Account opens from the title bar of the tab and shows the demo account.
        tap("nav.account")
        waitFor("account.signed_in_with")
        tap("account.done")
        waitFor("tab.codes")
    }

    /**
     * Licenses shows `NOTICE` and `OFL.txt`, then the third-party notices this
     * build generated (T087), each text in its runs of lines, in order, to the
     * last run.
     */
    @Test
    fun licensesShowTheThirdPartyNotices() {
        val texts = listOf("NOTICE", "OFL.txt", "third-party-notices.txt").map { name ->
            rule.activity.assets.open("licenses/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val runs = texts.flatMap(::licenseRuns)
        waitFor("demo.badge")
        tap("nav.account")
        tap("account.licenses")
        // The back arrow of a pushed screen is named common.back (zzThis #78).
        rule.onNodeWithTag("nav.back").assertContentDescriptionEquals(rule.activity.getString(R.string.common_back))
        // The texts are read off the main thread.
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(runs.first())).fetchSemanticsNodes().isNotEmpty() }
        // Items: the Demo badge, the heading, then every run.
        val list = rule.onNodeWithTag("licenses.list")
        for (run in listOf(0, 1, 2, runs.size / 2, runs.lastIndex)) {
            list.performScrollToIndex(2 + run)
            rule.onNode(hasText(runs[run])).assertIsDisplayed()
        }
        assertEquals(texts.joinToString("\n"), runs.joinToString("\n"))
    }
}
