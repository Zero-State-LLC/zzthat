// Storage (runtime.md, Modules and Storage): the FR-041 resolve cache, the
// draft, the last canonical text, the signed-in provider, the scan photo,
// and the FR-035 hidden codes, kept on the device only.
// Never a second source of truth, and never a no-store response.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zer0state.zzthat.storage"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
