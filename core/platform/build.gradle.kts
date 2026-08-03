plugins {
    id("basil.kmp.library")
}

import java.util.Properties

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.coroutines.core)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.exifinterface)
                implementation(libs.androidx.activity.compose)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.coroutines.swing)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

tasks.register("generateBasilConfig") {
    val configOutput = layout.buildDirectory.dir("generated/basilConfig/kotlin")
    val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")

    // Invalidate when local.properties changes so newly added keys are picked up.
    inputs.files(localPropertiesFile).optional().withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(configOutput)
    doLast {
        fun readGoogleServiceInfoIosClientId(): String? {
            val plist = rootProject.layout.projectDirectory.file("iosApp/iosApp/GoogleService-Info.plist").asFile
            if (!plist.exists()) return null
            val match = Regex("<key>CLIENT_ID</key>\\s*<string>([^<]+)</string>").find(plist.readText())
            return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }

        fun readLocalProperty(name: String): String? {
            val file = localPropertiesFile.asFile
            if (!file.exists()) return null
            val props = Properties()
            file.inputStream().use { props.load(it) }
            return props.getProperty(name)?.takeIf { it.isNotBlank() }
        }

        fun resolve(gradleKey: String, envKey: String, localKey: String): String =
            providers.gradleProperty(gradleKey).orNull
                ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
                ?: readLocalProperty(localKey)
                ?: ""

        val apiKey = resolve("basil.firebase.apiKey", "FIREBASE_WEB_API_KEY", "basil.firebase.apiKey")
        val projectId = resolve("basil.firebase.projectId", "FIREBASE_PROJECT_ID", "basil.firebase.projectId")
        val authDomain = resolve("basil.firebase.authDomain", "FIREBASE_AUTH_DOMAIN", "basil.firebase.authDomain")
            .ifBlank { if (projectId.isNotBlank()) "$projectId.firebaseapp.com" else "" }
        val storageBucket = resolve("basil.firebase.storageBucket", "FIREBASE_STORAGE_BUCKET", "basil.firebase.storageBucket")
            .ifBlank { if (projectId.isNotBlank()) "$projectId.appspot.com" else "" }
        val functionsRegion = resolve(
            "basil.firebase.functionsRegion",
            "FIREBASE_FUNCTIONS_REGION",
            "basil.firebase.functionsRegion",
        ).ifBlank { "us-central1" }
        val googleWebClientId = resolve(
            "basil.google.webClientId",
            "BASIL_GOOGLE_WEB_CLIENT_ID",
            "basil.google.webClientId",
        )
        val googleWebClientSecret = resolve(
            "basil.google.webClientSecret",
            "BASIL_GOOGLE_WEB_CLIENT_SECRET",
            "basil.google.webClientSecret",
        )
        val googleIosClientId = resolve(
            "basil.google.iosClientId",
            "BASIL_GOOGLE_IOS_CLIENT_ID",
            "basil.google.iosClientId",
        ).ifBlank { readGoogleServiceInfoIosClientId().orEmpty() }

        fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

        fun googleIosUrlScheme(clientId: String): String? {
            if (!clientId.endsWith(".apps.googleusercontent.com")) return null
            val prefix = clientId.removeSuffix(".apps.googleusercontent.com")
            return "com.googleusercontent.apps.$prefix"
        }

        val dir = configOutput.get().asFile
        dir.mkdirs()
        dir.resolve("com/joetr/basil/platform/BasilConfig.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.joetr.basil.platform

                object BasilConfig {
                    const val FIREBASE_API_KEY: String = "${esc(apiKey)}"
                    const val FIREBASE_PROJECT_ID: String = "${esc(projectId)}"
                    const val FIREBASE_AUTH_DOMAIN: String = "${esc(authDomain)}"
                    const val FIREBASE_STORAGE_BUCKET: String = "${esc(storageBucket)}"
                    const val FIREBASE_FUNCTIONS_REGION: String = "${esc(functionsRegion)}"
                    const val GOOGLE_WEB_CLIENT_ID: String = "${esc(googleWebClientId)}"
                    const val GOOGLE_WEB_CLIENT_SECRET: String = "${esc(googleWebClientSecret)}"
                    const val GOOGLE_IOS_CLIENT_ID: String = "${esc(googleIosClientId)}"
                }
                """.trimIndent(),
            )
        }
        googleIosUrlScheme(googleIosClientId)?.let { scheme ->
            configOutput.get().asFile.parentFile.resolve("ios-oauth-url-scheme.txt").writeText(scheme)
        }
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/basilConfig/kotlin"))
        }
    }
}

tasks.configureEach {
    if (name.contains("compile", ignoreCase = true) && name.contains("Kotlin", ignoreCase = true) ||
        name.contains("compile", ignoreCase = true) && name.contains("Main", ignoreCase = true)
    ) {
        dependsOn("generateBasilConfig")
    }
}
