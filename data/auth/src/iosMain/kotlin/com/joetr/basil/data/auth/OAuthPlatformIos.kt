package com.joetr.basil.data.auth

import com.joetr.basil.platform.openUrl

internal actual fun openOAuthUrl(url: String) {
    openUrl(url)
}

internal actual suspend fun awaitOAuthCallback(): String = OAuthCallbackGate.awaitCallback()
