import basil.libs

plugins {
    id("basil.compose.library")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":domain"))
                implementation(libs.findLibrary("coroutines-core").get())
                implementation(libs.findLibrary("coil-compose").get())
                implementation(libs.findLibrary("coil-network-ktor3").get())
            }
        }
    }
}
