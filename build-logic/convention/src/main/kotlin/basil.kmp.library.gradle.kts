import basil.configureBasilKmp
import basil.libraryNamespace
import basil.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
}

kotlin {
    configureBasilKmp(this)

    android {
        namespace = libraryNamespace()
        compileSdk = 36
        minSdk = 26
        androidResources {
            enable = false
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.findLibrary("coroutines-core").get())
            }
        }
    }
}
