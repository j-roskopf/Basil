package com.joetr.basil.data.auth

import com.joetr.basil.network.FirebaseSession

internal actual suspend fun readPlatformFirebaseSession(): FirebaseSession? = null

internal actual suspend fun writePlatformFirebaseSession(session: FirebaseSession?) = Unit
