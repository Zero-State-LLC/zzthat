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

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // The pinned shared vectors (contracts/vectors.json) are the test oracle.
    inputs.file(vectors).withPropertyName("vectors")
    inputs.dir(wordlists).withPropertyName("wordlists")
    systemProperty("zz.vectors", vectors.asFile.absolutePath)
    systemProperty("zz.wordlists", wordlists.asFile.absolutePath)
}
