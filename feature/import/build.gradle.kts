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
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
