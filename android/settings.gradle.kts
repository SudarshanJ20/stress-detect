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

rootProject.name = "stress-detect"

// SINGLE Gradle module. Packages (sensing / features / inference / ui / data) live inside
// it — see android/CLAUDE.md. Architecture is enforced by Konsist tests, not by module
// boundaries, so do NOT add sibling modules here.
include(":app")
