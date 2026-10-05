// Auth (runtime.md, Auth): the refresh token encrypted with an Android
// Keystore AES-GCM key and kept in DataStore, Google sign-in through Credential
// Manager with the server nonce, and the sign-in flow that leaves no half
// session. Android does not offer Apple (ZQ23).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zer0state.zzthat.auth"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
