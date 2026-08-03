plugins {
    id("basil.feature")
}

kotlin {
    sourceSets {
        androidMain {
            dependencies {
                implementation(libs.androidx.camera.core)
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)
                implementation(libs.mlkit.text.recognition)
                implementation(libs.androidx.activity.compose)
            }
        }
        commonMain {
            dependencies {
                implementation(project(":data:recipe"))
                implementation(project(":core:network"))
            }
        }
    }
}
