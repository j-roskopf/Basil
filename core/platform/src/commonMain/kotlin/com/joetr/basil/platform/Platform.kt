package com.joetr.basil.platform

public const val APP_NAME: String = "Basil"

public fun localDatabaseFileName(): String = "basil.db"

public fun localDatabaseRevisionKey(): String = "basil.db.revision"

public fun desktopDataDirectoryName(): String = ".basil"

public fun localStorageDirectoryName(): String = "basil"

/**
 * Coil on web fetches image bytes through Ktor, so cross-origin hosts must opt into CORS.
 * Route remote recipe images through the proxyImage Cloud Function on web.
 */
public fun imageUrlForDisplay(imageUrl: String?): String? =
    imageUrl?.let(::remoteImageUrlForPlatform)

/** Same routing as [imageUrlForDisplay], for downloading remote images on web. */
public fun remoteImageUrlForPlatform(imageUrl: String): String {
    if (platformName() != "web") return imageUrl
    if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) return imageUrl
    val projectId = BasilConfig.FIREBASE_PROJECT_ID
    if (projectId.isBlank()) return imageUrl
    val region = BasilConfig.FIREBASE_FUNCTIONS_REGION
    return "https://$region-$projectId.cloudfunctions.net/proxyImage?url=${encodeUrlParameter(imageUrl)}"
}

private fun encodeUrlParameter(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xff
        val safe =
            code in 'a'.code..'z'.code ||
                code in 'A'.code..'Z'.code ||
                code in '0'.code..'9'.code ||
                code == '-'.code || code == '_'.code || code == '.'.code || code == '~'.code
        if (safe) {
            append(code.toChar())
        } else {
            append('%')
            append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

public expect fun currentTimeMillis(): Long

public expect fun isNetworkAvailable(): Boolean

public expect fun openUrl(url: String)

public expect fun keepScreenOn(enabled: Boolean)

public expect fun hapticSuccess()

public expect fun playTimerCompleteSound()

public expect class ImageCapture {
    public companion object {
        public val isAvailable: Boolean
    }
}

public expect suspend fun resizeImage(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray

public expect suspend fun readClipboardText(): String?

public expect fun isDebugBuild(): Boolean

public expect fun platformName(): String
