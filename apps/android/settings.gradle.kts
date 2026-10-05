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

include(
    ":app",
    ":core-api",
    ":core-auth",
    ":core-design",
    ":core-grammar",
    ":core-storage",
)
