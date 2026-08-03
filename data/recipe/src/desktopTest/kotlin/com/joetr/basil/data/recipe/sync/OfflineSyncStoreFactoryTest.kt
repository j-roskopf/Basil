package com.joetr.basil.data.recipe.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.joetr.basil.database.basilDatabaseFromDriver
import com.joetr.basil.db.BasilDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class OfflineSyncStoreFactoryTest {
    @Test
    fun outboxTableIsCreatedWithSchema() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BasilDatabase.Schema.create(driver).await()
        val database = basilDatabaseFromDriver(driver)
        database.recipesQueries.enqueueOutbox("r1", "UPSERT", 1L)
        database.recipesQueries.enqueueOutbox("r1", "UPSERT", 2L) // replace
        assertEquals(1, database.recipesQueries.selectOutbox().executeAsList().size)
    }
}
