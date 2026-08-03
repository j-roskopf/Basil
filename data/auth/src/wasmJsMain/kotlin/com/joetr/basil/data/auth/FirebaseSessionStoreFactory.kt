package com.joetr.basil.data.auth

import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.network.FirebaseSession
import com.joetr.basil.network.FirebaseSessionStore

/** Web auth persists in localStorage only — avoids blocking on the in-memory SQL worker. */
private class LocalStorageFirebaseSessionStore : FirebaseSessionStore {
    override suspend fun load(): FirebaseSession? = WebFirebaseSessionStorage.load()

    override suspend fun save(session: FirebaseSession?) {
        WebFirebaseSessionStorage.save(session)
    }
}

public actual fun createFirebaseSessionStore(database: BasilDatabase): FirebaseSessionStore =
    LocalStorageFirebaseSessionStore()
