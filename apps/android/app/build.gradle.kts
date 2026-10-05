import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.zip.ZipInputStream

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

// Third-party notices (T087, Danny on #43): every library on the release
// runtime classpath with its group, name, version, and license, read from its
// POM (following parent POMs) out of Gradle's own cache, then each distinct
// license's text once, then any NOTICE file a library carries. The file is
// generated into the assets at build time and never edited by hand. A library
// with no license the build can find, or a license with no known text, fails
// the build instead of being skipped.

/** One license: its identifier and either its verbatim text (a file in app/licenses) or, for terms published only as a web page, its URL. */
data class KnownLicense(val id: String, val textFile: String?, val url: String?)

/** The licenses the release classpath uses, by normalized POM URL or name. Anything else fails the build. */
val knownLicenses: Map<String, KnownLicense> = run {
    val apache = KnownLicense("Apache-2.0", "Apache-2.0.txt", null)
    val bsd = KnownLicense("BSD-3-Clause", "BSD-3-Clause.txt", null)
    // Google publishes these terms only as web pages, so the notices give the URL.
    val androidSdk = KnownLicense("Android Software Development Kit License", null, "https://developer.android.com/studio/terms.html")
    val mlKit = KnownLicense("ML Kit Terms of Service", null, "https://developers.google.com/ml-kit/terms")
    mapOf(
        "apache.org/licenses/license-2.0" to apache,
        "apache-2.0" to apache,
        "the apache software license, version 2.0" to apache,
        "the apache license, version 2.0" to apache,
        "opensource.org/licenses/bsd-3-clause" to bsd,
        "bsd-3-clause" to bsd,
        "developer.android.com/studio/terms" to androidSdk,
        "developers.google.com/ml-kit/terms" to mlKit,
    )
}

abstract class GenerateThirdPartyNotices : DefaultTask() {
    /** The jar and aar files of the release runtime classpath, without the app's own modules. */
    @get:Internal
    lateinit var artifacts: ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    val artifactFiles: FileCollection
        get() = artifacts.artifactFiles

    /** Resolves POM files out of Gradle's cache. */
    @get:Internal
    lateinit var poms: DependencyHandler

    /** [knownLicenses]. It lives in this build file, so a change to it reruns the task. */
    @get:Internal
    lateinit var licenses: Map<String, KnownLicense>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenseTexts: DirectoryProperty

    /** Source copied into the app rather than resolved as an artifact: name, license identifier. */
    @get:Input
    abstract val copiedSources: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** A POM license URL or name, compared without its scheme, `www.`, or file extension. */
    private fun licenseKey(text: String): String = text.trim().lowercase()
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .removeSuffix("/").removeSuffix(".txt").removeSuffix(".html").removeSuffix(".htm")

    private fun pom(group: String, module: String, version: String): org.w3c.dom.Document? {
        val result = poms.createArtifactResolutionQuery().forModule(group, module, version)
            .withArtifacts(org.gradle.maven.MavenModule::class.java, org.gradle.maven.MavenPomArtifact::class.java).execute()
        val file = result.resolvedComponents.flatMap { it.getArtifacts(org.gradle.maven.MavenPomArtifact::class.java) }
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedArtifactResult>().firstOrNull()?.file ?: return null
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(file)
    }

    private fun child(element: org.w3c.dom.Element, name: String): String? =
        element.getElementsByTagName(name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** The POM's licenses as (name, URL), or its parent's, and so on up the chain. */
    private fun pomLicenses(group: String, module: String, version: String): List<Pair<String, String>> {
        var coordinates: Triple<String, String, String>? = Triple(group, module, version)
        val seen = mutableSetOf<Triple<String, String, String>>()
        while (coordinates != null && seen.add(coordinates)) {
            val document = pom(coordinates.first, coordinates.second, coordinates.third) ?: return emptyList()
            val nodes = document.getElementsByTagName("license")
            val found = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
                .map { (child(it, "name") ?: "") to (child(it, "url") ?: "") }
                .filter { it.first.isNotEmpty() || it.second.isNotEmpty() }
            if (found.isNotEmpty()) return found
            val parent = document.getElementsByTagName("parent").item(0) as? org.w3c.dom.Element
            coordinates = parent?.let { Triple(child(it, "groupId") ?: "", child(it, "artifactId") ?: "", child(it, "version") ?: "") }
        }
        return emptyList()
    }

    /** The NOTICE files inside a jar or aar, with the jars inside an aar: (path, verbatim text). */
    private fun noticeFiles(file: File): List<Pair<String, String>> {
        val notice = Regex("(?i)notice(\\.txt|\\.md)?")
        val found = mutableListOf<Pair<String, String>>()
        fun scan(zip: ZipInputStream, prefix: String) {
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    notice.matches(entry.name.substringAfterLast('/')) ->
                        found += "$prefix${entry.name}" to zip.readBytes().toString(Charsets.UTF_8)
                    // Not closed: closing it would close the outer stream.
                    entry.name.endsWith(".jar") -> scan(ZipInputStream(zip), "$prefix${entry.name}!/")
                }
            }
        }
        ZipInputStream(file.inputStream().buffered()).use { scan(it, "") }
        return found
    }

    @TaskAction
    fun generate() {
        val libraries = artifacts.artifacts
            .mapNotNull { artifact -> (artifact.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)?.let { it to artifact.file } }
            .groupBy({ "${it.first.group}:${it.first.module}:${it.first.version}" }) { it }
            .toSortedMap()
        val unlicensed = mutableListOf<String>()
        val unknown = sortedMapOf<String, MutableList<String>>()
        val entries = mutableListOf<Pair<String, List<KnownLicense>>>()
        val notices = mutableListOf<Pair<String, String>>()
        for ((coordinates, files) in libraries) {
            val id = files.first().first
            val found = pomLicenses(id.group, id.module, id.version)
            if (found.isEmpty()) {
                unlicensed += coordinates
                continue
            }
            val known = found.map { (name, url) ->
                licenses[licenseKey(url)] ?: licenses[licenseKey(name)]
                    ?: run { unknown.getOrPut("$name <$url>") { mutableListOf() } += coordinates; null }
            }
            entries += coordinates to known.filterNotNull().distinct()
            for ((_, file) in files) for ((path, text) in noticeFiles(file)) notices += "$coordinates $path" to text
        }
        for ((name, id) in copiedSources.get()) {
            val license = licenses.values.firstOrNull { it.id == id } ?: throw GradleException("copied source $name names an unknown license $id")
            entries += name to listOf(license)
        }
        if (unlicensed.isNotEmpty() || unknown.isNotEmpty()) {
            val problems = unlicensed.map { "no license in the POM or its parents: $it" } +
                unknown.map { (license, users) -> "no known text for $license, used by ${users.joinToString()}" }
            throw GradleException("Third-party notices are incomplete:\n" + problems.joinToString("\n"))
        }
        val rule = "=".repeat(64)
        val text = buildString {
            for ((name, licenseList) in entries) {
                append(name).append('\n')
                for (license in licenseList) append("    ").append(license.id).append('\n')
            }
            for (license in entries.flatMap { it.second }.distinct().sortedBy { it.id }) {
                append('\n').append(rule).append('\n').append(license.id).append('\n')
                if (license.url != null) append(license.url).append('\n')
                if (license.textFile != null) {
                    append('\n').append(licenseTexts.get().file(license.textFile).asFile.readText(Charsets.UTF_8).trimEnd()).append('\n')
                }
            }
            for ((name, notice) in notices) {
                append('\n').append(rule).append('\n').append(name).append('\n')
                append('\n').append(notice.trimEnd()).append('\n')
            }
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        File(out, "licenses").mkdirs()
        File(out, "licenses/third-party-notices.txt").writeText(text, Charsets.UTF_8)
    }
}

/** Every library on the release runtime classpath, one `group:name:version` per line, for the notices test. */
abstract class ListRuntimeLibraries : DefaultTask() {
    @get:Internal
    lateinit var artifacts: ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    val artifactFiles: FileCollection
        get() = artifacts.artifactFiles

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        val lines = artifacts.artifacts
            .mapNotNull { it.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .map { "${it.group}:${it.module}:${it.version}" }
            .distinct()
            .sorted()
        output.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

/** The release runtime classpath's libraries (all three flavors resolve the same ones), without the app's own modules. */
fun releaseArtifacts(): ArtifactCollection = configurations.getByName("prodReleaseRuntimeClasspath").incoming
    .artifactView { componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier } }
    .artifacts

val thirdPartyNotices = tasks.register<GenerateThirdPartyNotices>("generateThirdPartyNotices") {
    description = "Writes assets/licenses/third-party-notices.txt from the release runtime classpath."
    artifacts = releaseArtifacts()
    poms = project.dependencies
    licenses = knownLicenses
    licenseTexts.set(layout.projectDirectory.dir("licenses"))
    // Copied source, not an artifact (Danny, #43): the Scan tab's camera icon, PhotoCameraIcon.kt.
    copiedSources.put("github.com/google/material-design-icons src/image/photo_camera/materialicons/24px.svg", "Apache-2.0")
    outputDir.set(layout.buildDirectory.dir("generated/zz/notices"))
    notCompatibleWithConfigurationCache("resolves POM files when it runs")
}
val runtimeLibraries = tasks.register<ListRuntimeLibraries>("listReleaseRuntimeLibraries") {
    artifacts = releaseArtifacts()
    output.set(layout.buildDirectory.file("zz/release-runtime-libraries.txt"))
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val task = tasks.register<BundleWordlists>("bundle${name}Wordlists") {
            source.set(contractsDir.dir("wordlists"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, BundleWordlists::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(thirdPartyNotices, GenerateThirdPartyNotices::outputDir)
        // The unit tests read the merged manifest the variant ships (T072, T075).
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        tasks.withType<Test>().matching { it.name == "test${name}UnitTest" }.configureEach {
            inputs.file(manifest).withPropertyName("mergedManifest")
            jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dzz.manifest=" + manifest.get().asFile.absolutePath) })
        }
    }
}

tasks.withType<Test>().configureEach {
    // The notices tests read the generated file and the release classpath list (T087).
    val notices = thirdPartyNotices.flatMap { it.outputDir }
    val libraries = runtimeLibraries.flatMap { it.output }
    inputs.dir(notices).withPropertyName("thirdPartyNotices")
    inputs.file(libraries).withPropertyName("releaseRuntimeLibraries")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dzz.notices=" + notices.get().file("licenses/third-party-notices.txt").asFile.absolutePath,
            "-Dzz.releaseLibraries=" + libraries.get().asFile.absolutePath,
        )
    })
    systemProperty("zz.appSrc", layout.projectDirectory.dir("src").asFile.absolutePath)
    systemProperty("zz.apps", appsDir.asFile.absolutePath)
    systemProperty("zz.wordlists", contractsDir.dir("wordlists").asFile.absolutePath)
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
    implementation(libs.compose.material.icons.core)
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
