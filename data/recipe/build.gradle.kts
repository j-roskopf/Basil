plugins {
    id("basil.data")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:database"))
                implementation(project(":core:platform"))
                implementation(project(":core:network"))
                implementation(project(":data:image"))
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.sqldelight.async.extensions)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
    }
}
