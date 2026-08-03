package com.joetr.basil.platform

import kotlinx.browser.window

private val epochMillis: () -> Double =
    js("() => Date.now()")

public actual fun currentTimeMillis(): Long = epochMillis().toLong()

public actual fun isNetworkAvailable(): Boolean = window.navigator.onLine

public actual fun openUrl(url: String) {
    window.open(url, "_blank")
}

public actual fun keepScreenOn(enabled: Boolean) = Unit

public actual fun hapticSuccess() = Unit

public actual fun playTimerCompleteSound() = Unit

public actual class ImageCapture {
    public actual companion object {
        public actual val isAvailable: Boolean = false
    }
}

public actual suspend fun resizeImage(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray = bytes

public actual suspend fun readClipboardText(): String? = null

public actual fun isDebugBuild(): Boolean = window.location.hostname == "localhost"

public actual fun platformName(): String = "web"
