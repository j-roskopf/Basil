import basil.libs

plugins {
    id("basil.kmp.library")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.findLibrary("serialization-json").get())
                implementation(project(":core:platform"))
            }
        }
    }
}
