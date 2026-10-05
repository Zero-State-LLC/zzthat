// The pinned design (runtime.md, Modules: Design): Tokens.kt from zzThis
// design/generated, the IBM Plex TTF files in res/font, and the license texts
// in assets/licenses. Values here are never authored in this repo.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zer0state.zzthat.design"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

val fonts = layout.projectDirectory.dir("src/main/res/font")
val licenses = layout.projectDirectory.dir("src/main/assets/licenses")

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.text)
    api(libs.compose.animation.core)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("zz.fonts", fonts.asFile.absolutePath)
    systemProperty("zz.licenses", licenses.asFile.absolutePath)
}
