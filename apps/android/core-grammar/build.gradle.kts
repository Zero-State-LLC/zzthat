// Plain Kotlin JVM module (runtime.md, Modules): zzThis Section 2.2a, the
// spec 003 check word, the spec 004 scanner and bands, and the list loader.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

val contracts = rootProject.layout.projectDirectory.dir("../../contracts")
val vectors = contracts.file("vectors.json")
val wordlists = contracts.dir("wordlists")
// The band rows are one shared table for both platforms (runtime.md,
// Testing; tasks.md T022). The file lives with the ZZCore tests and is read
// in place, so there is one copy.
val bandRows = rootProject.layout.projectDirectory.file(
    "../ios/Packages/ZZCore/Tests/ZZCoreTests/Fixtures/band-rows.json",
)

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // The pinned shared vectors (contracts/vectors.json) are the test oracle.
    inputs.file(vectors).withPropertyName("vectors")
    inputs.dir(wordlists).withPropertyName("wordlists")
    inputs.file(bandRows).withPropertyName("bandRows")
    systemProperty("zz.vectors", vectors.asFile.absolutePath)
    systemProperty("zz.wordlists", wordlists.asFile.absolutePath)
    systemProperty("zz.bandRows", bandRows.asFile.absolutePath)
}
