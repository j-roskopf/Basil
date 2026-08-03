plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

android {
    namespace = "com.joetr.basil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joetr.basil"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("basil.versionCode").getOrElse("1").toInt()
        versionName = providers.gradleProperty("basil.versionName").getOrElse("0.1.0")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
