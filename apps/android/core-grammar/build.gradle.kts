// Plain Kotlin JVM module (runtime.md, Modules): zzThis Section 2.2a, the
// spec 003 check word, the spec 004 scanner and bands, and the list loader.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

val vectors = rootProject.layout.projectDirectory.file("../../contracts/vectors.json")

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // The pinned shared vectors (contracts/vectors.json) are the test oracle.
    inputs.file(vectors).withPropertyName("vectors")
    systemProperty("zz.vectors", vectors.asFile.absolutePath)
}
