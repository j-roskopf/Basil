import basil.libs

plugins {
    id("basil.compose.library")
    id("basil.metro")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":domain"))
                implementation(project(":ui"))
                implementation(project(":navigation"))
                implementation(project(":core:platform"))
                implementation(libs.findLibrary("coroutines-core").get())
                implementation(libs.findLibrary("lifecycle-viewmodel").get())
                implementation(libs.findLibrary("lifecycle-viewmodel-compose").get())
                implementation(libs.findLibrary("lifecycle-runtime-compose").get())
            }
        }
        named("commonTest") {
            dependencies {
                implementation(libs.findLibrary("coroutines-test").get())
            }
        }
    }
}
