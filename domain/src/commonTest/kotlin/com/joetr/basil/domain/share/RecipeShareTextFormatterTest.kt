package com.joetr.basil.domain.share

import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeShareTextFormatterTest {
    @Test
    fun formatsRecipeForRecipientsWithoutBasil() {
        val text = RecipeShareTextFormatter.format(
            Recipe(
                id = "recipe-1",
                ownerId = "owner-1",
                title = "Lemon pasta",
                description = "Bright and quick.",
                sourceUrl = "https://example.com/lemon-pasta",
                servings = 2,
                prepMinutes = 10,
                ingredients = listOf("200 g pasta", "1 lemon"),
                steps = listOf(RecipeStep("Boil the pasta", minutes = 8)),
                createdAt = 1L,
                updatedAt = 1L,
            ),
            link = "https://basil.joetr.com/share/token",
        )

        assertEquals(
            """Lemon pasta

Bright and quick.

2 servings · prep 10 min

Ingredients
• 200 g pasta
• 1 lemon

Steps
1. Boil the pasta (8 min)

Source: https://example.com/lemon-pasta

Shared from Basil: https://basil.joetr.com/share/token""",
            text,
        )
    }

    @Test
    fun omitsEmptySections() {
        val text = RecipeShareTextFormatter.format(
            Recipe(
                id = "recipe-1",
                ownerId = "owner-1",
                title = "Untitled",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        assertEquals("Untitled", text)
        assertTrue("Ingredients" !in text)
        assertTrue("Steps" !in text)
    }
}
