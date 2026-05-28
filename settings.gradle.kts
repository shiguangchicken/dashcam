pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "DashCam"

include(":app")
include(":core-common")
include(":core-database")
include(":core-media")
include(":core-network")
include(":core-voice")
include(":feature-recorder")
include(":feature-remote")
include(":feature-settings")
include(":benchmark")
include(":test-robot")
