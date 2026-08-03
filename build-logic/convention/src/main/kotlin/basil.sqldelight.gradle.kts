plugins {
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("BasilDatabase") {
            packageName.set("com.joetr.basil.db")
            generateAsync.set(true)
        }
    }
}
