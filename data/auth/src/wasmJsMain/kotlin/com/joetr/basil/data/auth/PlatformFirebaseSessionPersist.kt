package com.joetr.basil.data.auth

import com.joetr.basil.network.FirebaseSession

internal actual fun persistFirebaseSessionToPlatform(session: FirebaseSession) {
    WebFirebaseSessionStorage.save(session)
}
