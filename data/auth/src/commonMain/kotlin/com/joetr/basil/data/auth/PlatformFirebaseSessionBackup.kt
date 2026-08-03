package com.joetr.basil.data.auth

import com.joetr.basil.network.FirebaseSession

internal expect suspend fun readPlatformFirebaseSession(): FirebaseSession?

internal expect suspend fun writePlatformFirebaseSession(session: FirebaseSession?)
