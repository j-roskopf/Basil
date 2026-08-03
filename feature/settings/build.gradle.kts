plugins {
    id("basil.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:auth"))
                implementation(project(":data:recipe"))
                implementation(project(":core:updates"))
                implementation(project(":core:platform"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
