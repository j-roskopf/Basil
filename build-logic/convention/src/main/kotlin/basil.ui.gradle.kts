import basil.libs

plugins {
    id("basil.compose.library")
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("coil3.annotation.ExperimentalCoilApi")
        }
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
