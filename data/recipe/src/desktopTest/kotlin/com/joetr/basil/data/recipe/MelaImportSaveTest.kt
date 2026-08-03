package com.joetr.basil.data.recipe

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.database.basilDatabaseFromDriver
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import com.joetr.basil.domain.parser.MelaRecipeParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MelaImportSaveTest {
    @Test
    fun saveMelaRecipe_preservesIngredientsAndSteps() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BasilDatabase.Schema.create(driver).await()
        val database = basilDatabaseFromDriver(driver)
        val repository = DefaultRecipeRepository(database, syncService = null, imageRepository = null)

        val bytes = readResource("general-tso.melarecipe")
        val item = MelaRecipeParser.parseRecipeJson(bytes)
        assertTrue(item != null)
        assertTrue(item.extracted.ingredients.isNotEmpty())
        assertTrue(item.extracted.steps.isNotEmpty())

        repository.save(
            Recipe(
                id = "general-tso",
                ownerId = "owner",
                title = item.extracted.title ?: "General Tso",
                description = item.extracted.description,
                ingredients = item.extracted.ingredients,
                steps = item.extracted.steps,
                createdAt = 1L,
                updatedAt = 0L,
            ),
            syncImmediately = false,
        )

        val row = database.recipesQueries.selectRecipeById("general-tso").executeAsOne()
        assertTrue(row.ingredients != "[]", "ingredients column: ${row.ingredients.take(80)}")
        assertTrue(row.steps != "[]", "steps column: ${row.steps.take(80)}")

        val loaded = repository.getAllByOwner("owner").single()
        assertTrue(loaded.ingredients.isNotEmpty())
        assertTrue(loaded.steps.isNotEmpty())
    }

    @Test
    fun partialRecipeSave_wipesIngredientsAndSteps() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BasilDatabase.Schema.create(driver).await()
        val database = basilDatabaseFromDriver(driver)
        val repository = DefaultRecipeRepository(database, syncService = null, imageRepository = null)

        repository.save(
            Recipe(
                id = "r1",
                ownerId = "owner",
                title = "General Tso",
                description = "Crispy chicken",
                ingredients = listOf("1 lb chicken"),
                steps = listOf(RecipeStep("Cook chicken")),
                createdAt = 1L,
                updatedAt = 0L,
            ),
            syncImmediately = false,
        )

        repository.save(
            Recipe(
                id = "r1",
                ownerId = "owner",
                title = "General Tso",
                imageUrl = "https://firebasestorage.googleapis.com/v0/b/test/o/image.jpg",
                createdAt = 1L,
                updatedAt = 0L,
            ),
            syncImmediately = false,
        )

        val loaded = repository.getAllByOwner("owner").single()
        assertEquals(emptyList(), loaded.ingredients)
        assertEquals(emptyList(), loaded.steps)
        assertEquals(null, loaded.description)
    }

    private fun readResource(name: String): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: error("Missing resource: $name")
        return stream.use { it.readBytes() }
    }
}
