plugins {
    id("basil.data")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":core:network"))
                implementation(libs.ktor.client.core)
            }
        }
    }
}
