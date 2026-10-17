package com.zer0state.zzthat.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zer0state.zzthat.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-12 and runtime.md, End-to-end run: against the local zzThis Worker
 * (`npm run dev:api` at the pin, reached from the emulator at 10.0.2.2:8787),
 * in the local flavor, the only build with developer sign-in. apps-e2e.yml
 * runs it; a missing Worker fails it.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(tag: String, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { exists(tag) }
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true)
        try {
            node.performScrollTo()
        } catch (error: AssertionError) {
            // Not inside a scrolling screen.
        }
        node.performClick()
        rule.waitForIdle()
    }

    /**
     * The Terms step before the first create on this device (design/UX.md,
     * Terms): confirm 18 or older and accept the Terms, when it shows. A fresh
     * install always shows it, but a slow emulator can take a while to read
     * the stored acceptance, so this waits as long as every other step.
     */
    private fun confirmTermsIfAsked() {
        try {
            rule.waitUntil(15_000) { exists("terms.age_confirm") }
        } catch (error: ComposeTimeoutException) {
            return
        }
        tap("terms.age_confirm")
        tap("terms.accept")
    }

    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun description(tag: String): String = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()

    private fun text(tag: String): String = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun typeCode(code: String) {
        tap("scan.type_instead")
        rule.waitUntil(15_000) { exists("type.field") }
        rule.onNodeWithTag("type.field").performTextInput(code)
        tap("type.open")
    }

    @Test
    fun developerSignInCreateRerollRevokeAndDelete() {
        // Sign in with developer sign-in from Create. A fresh install starts signed out.
        tap("tab.create")
        rule.waitUntil(15_000) { exists("create.sign_in") || exists("create.title") }
        if (exists("create.sign_in")) {
            tap("create.sign_in")
            tap("signin.dev")
        }
        // Create a code titled E2E.
        rule.waitUntil(15_000) { exists("create.title") }
        rule.onNodeWithTag("create.title").performTextInput("E2E")
        confirmTermsIfAsked()
        tap("create.submit")
        // Re-roll until reroll-cap: the button goes away.
        rule.waitUntil(15_000) { exists("minted.reroll") }
        while (exists("minted.reroll")) {
            val before = description("minted.code")
            tap("minted.reroll")
            rule.waitUntil(15_000) { !exists("minted.reroll") || description("minted.code") != before }
        }
        val code = description("minted.code")
        assertTrue(code, code.startsWith("zz-") && code.endsWith("-zz"))
        // Sign out, type the code, and see E2E.
        back()
        tap("nav.account")
        tap("account.sign_out")
        tap("account.done")
        tap("tab.scan")
        typeCode(code)
        rule.waitUntil(15_000) { exists("record.title") }
        assertEquals("E2E", text("record.title"))
        // Sign in again, revoke the code, and see not-found.
        back()
        back()
        tap("nav.account")
        tap("signin.dev")
        rule.waitUntil(15_000) { exists("account.signed_in_with") }
        tap("account.done")
        tap("tab.codes")
        // The row of this run's code, found by its words.
        val row = hasTestTagPrefix("codes.row.") and hasContentDescription(code)
        rule.waitUntil(15_000) { rule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(row).onFirst().performClick()
        tap("detail.revoke")
        tap("revoke.confirm.confirm")
        rule.waitUntil(15_000) { !exists("detail.revoke") }
        back()
        tap("tab.scan")
        typeCode(code)
        rule.waitUntil(15_000) { exists("message.resolve_not_found") }
        // Delete the account.
        back()
        back()
        tap("nav.account")
        tap("account.delete")
        tap("delete.confirm.confirm")
        rule.waitUntil(15_000) { exists("signin.dev") }
    }

    private fun hasTestTagPrefix(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
}
