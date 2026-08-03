package com.joetr.basil.data.auth

import com.joetr.basil.network.BasilFirebase

public actual suspend fun googleSignIn(firebase: BasilFirebase): GoogleSignInResult {
    val redirectUri = webOAuthRedirectUri()
    return googleSignInWithAuthorizationCodeFlow(
        firebase = firebase,
        redirectUri = redirectUri,
        openUrl = ::openOAuthUrl,
        awaitCallback = ::awaitOAuthCallback,
        onAuthorizeRequest = { persistWebOAuthState(it.state) },
    )
}
