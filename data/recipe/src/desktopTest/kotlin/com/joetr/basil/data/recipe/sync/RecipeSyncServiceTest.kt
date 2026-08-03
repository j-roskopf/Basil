package com.joetr.basil.data.recipe.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.joetr.basil.database.basilDatabaseFromDriver
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.network.BasilFirebase
import com.joetr.basil.network.FirebaseSession
import com.joetr.basil.network.FirebaseSessionStore
import com.joetr.basil.network.createBasilHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RecipeSyncServiceTest {
    @Test
    fun enqueueAddsOutboxEntry() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BasilDatabase.Schema.create(driver).await()
        val database = basilDatabaseFromDriver(driver)
        val firebase = BasilFirebase.create(
            httpClient = createBasilHttpClient(),
            sessionStore = object : FirebaseSessionStore {
                override suspend fun load(): FirebaseSession? = null
                override suspend fun save(session: FirebaseSession?) = Unit
            },
        )
        val service = RecipeSyncService(database, firebase)
        val recipe = Recipe(
            id = "r1",
            ownerId = "owner",
            title = "Soup",
            createdAt = 1L,
            updatedAt = 1L,
        )
        database.recipesQueries.insertRecipe(
            id = recipe.id,
            owner_id = recipe.ownerId,
            title = recipe.title,
            description = null,
            image_url = null,
            local_image_id = null,
            source_url = null,
            servings = null,
            prep_minutes = null,
            cook_minutes = null,
            ingredients = "[]",
            steps = "[]",
            tags = "[]",
            notes = null,
            is_favourite = 0,
            created_at = 1,
            updated_at = 1,
            deleted = 0,
            pending_sync = 0,
        )
        service.enqueueRecipe(recipe)
        val pending = database.recipesQueries.selectOutbox().executeAsList()
        assertEquals(1, pending.size)
        assertEquals("UPSERT", pending.first().kind)
        assertTrue(database.recipesQueries.selectPendingSync().executeAsList().isNotEmpty())
    }

    @Test
    fun enqueueDeleteAddsDeleteOutboxEntry() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BasilDatabase.Schema.create(driver).await()
        val database = basilDatabaseFromDriver(driver)
        val firebase = BasilFirebase.create(
            httpClient = createBasilHttpClient(),
            sessionStore = object : FirebaseSessionStore {
                override suspend fun load(): FirebaseSession? = null
                override suspend fun save(session: FirebaseSession?) = Unit
            },
        )
        val service = RecipeSyncService(database, firebase)
        database.recipesQueries.insertRecipe(
            id = "r1",
            owner_id = "owner",
            title = "Soup",
            description = null,
            image_url = null,
            local_image_id = null,
            source_url = null,
            servings = null,
            prep_minutes = null,
            cook_minutes = null,
            ingredients = "[]",
            steps = "[]",
            tags = "[]",
            notes = null,
            is_favourite = 0,
            created_at = 1,
            updated_at = 1,
            deleted = 0,
            pending_sync = 0,
        )
        database.recipesQueries.softDeleteRecipe(updated_at = 2, id = "r1")
        service.enqueueDelete("r1", updatedAt = 2)
        val pending = database.recipesQueries.selectOutbox().executeAsList()
        assertEquals(1, pending.size)
        assertEquals("DELETE", pending.first().kind)
        val row = database.recipesQueries.selectRecipeByIdAny("r1").executeAsOne()
        assertEquals(1L, row.deleted)
        assertEquals(1L, row.pending_sync)
    }
}
