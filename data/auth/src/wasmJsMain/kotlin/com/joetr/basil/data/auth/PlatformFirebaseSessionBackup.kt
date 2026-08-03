package com.joetr.basil.data.auth

import com.joetr.basil.network.FirebaseSession

internal actual suspend fun readPlatformFirebaseSession(): FirebaseSession? =
    WebFirebaseSessionStorage.load()

internal actual suspend fun writePlatformFirebaseSession(session: FirebaseSession?) {
    WebFirebaseSessionStorage.save(session)
}
