package com.joetr.basil.data.auth

import com.joetr.basil.network.BasilFirebase

/**
 * Completes a Google OAuth redirect callback when the web app loads at `/auth-callback`.
 * No-op on platforms that do not use a browser redirect (Android, desktop loopback, etc.).
 */
public expect suspend fun resumeWebOAuthSignIn(firebase: BasilFirebase): GoogleSignInResult?
