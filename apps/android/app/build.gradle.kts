import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

// The application (runtime.md, Modules: App): entry, tabs, navigation, feature
// screens, and dependency wiring. Flavors demo, local, and prod are the Demo,
// Local, and Release builds (runtime.md, Build configurations).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

val appsDir = rootProject.layout.projectDirectory.dir("..")
val contractsDir = rootProject.layout.projectDirectory.dir("../../contracts")
val appVersion = appsDir.file("VERSION").asFile.readText().trim()

/** A Release value injected at build time (-Pname=value), never committed. */
fun injected(name: String): String = providers.gradleProperty(name).orNull.orEmpty()

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.zer0state.zzthat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zer0state.zzthat"
        minSdk = 26
        targetSdk = 36
        // apps/VERSION is the user-visible version. Build numbers come from
        // the CI run number (runtime.md, Release step 1).
        versionName = appVersion
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The support contact and the privacy link (FR-047, ZQ25). Not
        // secrets. Release injects its own values (below).
        buildConfigField("String", "SUPPORT_EMAIL", quoted("support@zer0state.com"))
        buildConfigField("String", "PRIVACY_URL", quoted("https://zero-state-llc.github.io/zzthis/privacy/"))
    }

    flavorDimensions += "build"
    productFlavors {
        create("demo") {
            dimension = "build"
            applicationIdSuffix = ".demo"
            manifestPlaceholders["appLabel"] = "zzThat Demo"
            buildConfigField("String", "BUILD_KIND", quoted("demo"))
            // Demo makes no network call: the in-memory demo API answers.
            buildConfigField("String", "API_ORIGIN", quoted(""))
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(""))
        }
        create("local") {
            dimension = "build"
            applicationIdSuffix = ".local"
            manifestPlaceholders["appLabel"] = "zzThat Local"
            buildConfigField("String", "BUILD_KIND", quoted("local"))
            // The local zzThis Worker (npm run dev:api) from the emulator.
            buildConfigField("String", "API_ORIGIN", quoted("http://10.0.2.2:8787"))
            // A placeholder until Danny creates the Google clients. The Google
            // button stays hidden while it is "unset".
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted("unset"))
        }
        create("prod") {
            dimension = "build"
            manifestPlaceholders["appLabel"] = "zzThat"
            buildConfigField("String", "BUILD_KIND", quoted("prod"))
            // Injected at build time (-PzzApiOrigin=..., and so on), never
            // committed. Without them every call answers error.unavailable.
            buildConfigField("String", "API_ORIGIN", quoted(injected("zzApiOrigin")))
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(injected("zzGoogleWebClientId").ifEmpty { "unset" }))
            buildConfigField("String", "SUPPORT_EMAIL", quoted(injected("zzSupportEmail")))
            buildConfigField("String", "PRIVACY_URL", quoted(injected("zzPrivacyUrl")))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}

// contracts/wordlists/*.txt are bundled as assets/wordlists (runtime.md,
// Pinned files; FR-044). The pinned folder is copied at build time, not
// duplicated in the tree.
abstract class BundleWordlists : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val target = File(out, "wordlists").apply { mkdirs() }
        source.get().asFile.listFiles { file -> file.name.endsWith(".txt") }.orEmpty().forEach { it.copyTo(File(target, it.name)) }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val task = tasks.register<BundleWordlists>("bundle${name}Wordlists") {
            source.set(contractsDir.dir("wordlists"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, BundleWordlists::outputDir)
        // The unit tests read the merged manifest the variant ships (T072, T075).
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        tasks.withType<Test>().matching { it.name == "test${name}UnitTest" }.configureEach {
            inputs.file(manifest).withPropertyName("mergedManifest")
            jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dzz.manifest=" + manifest.get().asFile.absolutePath) })
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("zz.appSrc", layout.projectDirectory.dir("src").asFile.absolutePath)
    systemProperty("zz.apps", appsDir.asFile.absolutePath)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-auth"))
    implementation(project(":core-design"))
    implementation(project(":core-grammar"))
    implementation(project(":core-storage"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.base)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
