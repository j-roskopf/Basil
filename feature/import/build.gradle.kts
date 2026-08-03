plugins {
    id("basil.feature")
}

kotlin {
    android {
        namespace = "com.joetr.basil.feature.urlimport"
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:recipe"))
                implementation(project(":core:network"))
                implementation(project(":core:platform"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
