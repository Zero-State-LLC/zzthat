pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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

// Only modules that have a build script are included. Gradle refuses to
// configure a project whose directory does not exist, so listing an unwritten
// module breaks every task, including the client generation in Apps CI.
// Not written yet (runtime.md, Modules), added here as each one lands:
//   ":app"           (only synced resources so far, no build script)
//   ":core-auth"     (no directory yet)
//   ":core-design"   (only synced tokens, fonts, and licenses, no build script)
//   ":core-storage"  (no directory yet)
include(
    ":core-api",
    ":core-grammar",
)
