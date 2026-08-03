plugins {
    id("basil.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:recipe"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
            }
        }
    }
}
