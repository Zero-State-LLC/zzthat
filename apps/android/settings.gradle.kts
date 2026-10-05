// Google Maven and Maven Central only. AGP, the Kotlin plugins, and Paparazzi
// publish their plugin markers there. The OpenAPI Generator runs from its CLI
// artifact on Maven Central (core-api/build.gradle.kts).
pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "zzThat"

include(
    ":app",
    ":core-api",
    ":core-auth",
    ":core-design",
    ":core-grammar",
    ":core-storage",
)
