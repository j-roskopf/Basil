package com.joetr.basil.domain.export

import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasilRecipeCodecTest {
    @Test
    fun roundTripPreservesRecipeFields() {
        val recipe = Recipe(
            id = "recipe-1",
            ownerId = "owner-1",
            title = "Tomato Soup",
            description = "Comfort food",
            imageUrl = "https://example.com/soup.jpg",
            localImageId = "local-1",
            sourceUrl = "https://example.com/recipe",
            servings = 4,
            prepMinutes = 10,
            cookMinutes = 30,
            ingredients = listOf("tomatoes", "onion"),
            steps = listOf(RecipeStep("Simmer", minutes = 20)),
            tags = listOf("soup"),
            notes = "Season to taste",
            isFavourite = true,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
        )
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        val exported = BasilRecipeCodec.export(
            recipes = listOf(recipe),
            imageBytesByLocalId = mapOf("local-1" to imageBytes),
            exportedAt = 1_700_000_200_000L,
        )

        assertEquals(1, exported.recipeCount)
        val parsed = BasilRecipeCodec.parse(exported.bytes)
        assertEquals(1, parsed.size)
        val entry = parsed.single().entry
        assertEquals("Tomato Soup", entry.title)
        assertEquals("Comfort food", entry.description)
        assertEquals("https://example.com/soup.jpg", entry.imageUrl)
        assertEquals("https://example.com/recipe", entry.sourceUrl)
        assertEquals(4, entry.servings)
        assertEquals(10, entry.prepMinutes)
        assertEquals(30, entry.cookMinutes)
        assertEquals(listOf("tomatoes", "onion"), entry.ingredients)
        assertEquals(listOf(RecipeStep("Simmer", minutes = 20)), entry.steps)
        assertEquals(listOf("soup"), entry.tags)
        assertEquals("Season to taste", entry.notes)
        assertTrue(entry.isFavourite)
        assertEquals(1_700_000_000_000L, entry.createdAt)
        assertTrue(parsed.single().imageBytes.contentEquals(imageBytes))
    }
}
