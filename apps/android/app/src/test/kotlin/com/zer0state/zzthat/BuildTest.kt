package com.zer0state.zzthat

import com.zer0state.zzthat.app.BuildKind
import com.zer0state.zzthat.app.BuildSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The built variant (tasks.md T071, T072, T075; runtime.md, Build
 * configurations, Privacy, and Release). Each flavor's unit tests check their
 * own build.
 */
class BuildTest {
    private val config = BuildSettings.current
    private val manifest: Element by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File(System.getProperty("zz.manifest"))).documentElement
    }
    private val android = "http://schemas.android.com/apk/res/android"

    /** runtime.md, Build configurations: the ids, and what each build talks to. */
    @Test
    fun theFlavorIsTheBuildConfiguration() {
        when (config.kind) {
            BuildKind.DEMO -> {
                assertEquals("com.zer0state.zzthat.demo", BuildConfig.APPLICATION_ID)
                assertNull("Demo makes no network call", config.apiOrigin)
                assertEquals("", BuildConfig.GOOGLE_WEB_CLIENT_ID)
                assertFalse(config.offersDeveloperSignIn)
            }
            BuildKind.LOCAL -> {
                assertEquals("com.zer0state.zzthat.local", BuildConfig.APPLICATION_ID)
                assertEquals("http://10.0.2.2:8787", config.apiOrigin)
                assertEquals("unset", config.googleWebClientId)
                assertTrue("Local offers developer sign-in (FR-048)", config.offersDeveloperSignIn)
            }
            BuildKind.PROD -> {
                assertEquals("com.zer0state.zzthat", BuildConfig.APPLICATION_ID)
                assertFalse("Release compiles developer sign-in out (FR-048)", config.offersDeveloperSignIn)
            }
        }
    }

    /** The display name per build (runtime.md, Build configurations). */
    @Test
    fun theDisplayName() {
        val label = (manifest.getElementsByTagName("application").item(0) as Element).getAttributeNS(android, "label")
        val expected = mapOf(BuildKind.DEMO to "zzThat Demo", BuildKind.LOCAL to "zzThat Local", BuildKind.PROD to "zzThat")
        assertEquals(expected.getValue(config.kind), label)
    }

    /** FR-047: the support contact and the privacy link are build settings, injected for Release. */
    @Test
    fun supportAndPrivacyComeFromBuildSettings() {
        if (config.kind == BuildKind.PROD) return assertEquals("injected at build time", BuildConfig.SUPPORT_EMAIL, config.supportEmail.orEmpty())
        assertEquals("support@zer0state.com", config.supportEmail)
        assertEquals("https://zero-state-llc.github.io/zzthis/privacy/", config.privacyUrl)
    }

    /** Release step 1: apps/VERSION is the user-visible version. */
    @Test
    fun theVersionIsAppsVersion() {
        assertEquals(File(System.getProperty("zz.apps"), "VERSION").readText().trim(), BuildConfig.VERSION_NAME)
    }

    /**
     * T072 and FR-011 to FR-014: no camera, location, microphone, or photo
     * library permission, no advertising id, and no backup of the encrypted
     * token (runtime.md, Auth step 6). Android uses the system camera app and
     * the system photo picker, which need none.
     */
    @Test
    fun theManifestAsksForNoCameraLocationMicrophoneOrPhotos() {
        val nodes = manifest.getElementsByTagName("uses-permission")
        val permissions = (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(android, "name") }.toSet()
        val refused = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
        )
        assertTrue(permissions.intersect(refused).toString(), permissions.intersect(refused).isEmpty())
        val application = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
    }

    /** Plain HTTP only in Local, and only to the local Worker (runtime.md, Build configurations). */
    @Test
    fun plainHttpOnlyInLocal() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val networkConfig = application.getAttributeNS(android, "networkSecurityConfig")
        assertEquals(config.kind == BuildKind.LOCAL, networkConfig.isNotEmpty())
        assertFalse(application.getAttributeNS(android, "usesCleartextTraffic") == "true")
    }
}
