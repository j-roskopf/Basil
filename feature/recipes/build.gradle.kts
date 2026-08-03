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
    }
}
