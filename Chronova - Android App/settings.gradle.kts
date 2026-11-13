pluginManagement {
    includeBuild("build-logic")
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Chronova"
include(":app")
include(":core:model")
include(":core:timeline")
include(":core:domain")
include(":core:testing")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":feature:notifications:domain")
include(":feature:notifications:data")
include(":feature:notifications:presentation")
include(":feature:projects:domain")
include(":feature:projects:presentation")
include(":feature:timeline:domain")
include(":feature:timeline:presentation")
include(":feature:sync:domain")
include(":feature:sync:data")
include(":feature:sync:presentation")
include(":feature:account:domain")
include(":feature:account:data")
include(":feature:account:presentation")
include(":feature:assistant:domain")
include(":feature:assistant:data")
include(":feature:assistant:presentation")
include(":feature:export:domain")
include(":feature:export:data")
include(":feature:export:presentation")
include(":feature:reminders:domain")
include(":feature:reminders:data")
include(":feature:push:domain")
include(":feature:push:data")
