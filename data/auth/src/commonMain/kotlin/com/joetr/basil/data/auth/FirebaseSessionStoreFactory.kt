package com.joetr.basil.data.auth

import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.network.FirebaseSessionStore

public expect fun createFirebaseSessionStore(database: BasilDatabase): FirebaseSessionStore
