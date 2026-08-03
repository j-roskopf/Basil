import basil.libs

plugins {
    id("basil.kmp.library")
    kotlin("plugin.serialization")
    id("basil.metro")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":domain"))
                implementation(project(":core:network"))
                implementation(project(":core:database"))
                implementation(libs.findLibrary("coroutines-core").get())
                implementation(libs.findLibrary("serialization-json").get())
            }
        }
        named("commonTest") {
            dependencies {
                implementation(libs.findLibrary("coroutines-test").get())
            }
        }
    }
}
