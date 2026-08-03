pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Basil"

include(":androidApp")
include(":composeApp")
include(":domain")
include(":navigation")
include(":ui")
include(":core:database")
include(":core:network")
include(":core:platform")
include(":core:updates")
include(":data:recipe")
include(":data:auth")
include(":data:image")
include(":feature:recipes")
include(":feature:cook")
include(":feature:editor")
include(":feature:import")
include(":feature:scan")
include(":feature:auth")
include(":feature:settings")
