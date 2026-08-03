package com.joetr.basil.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeSearchTest {
    private val recipe = Recipe(
        id = "1",
        ownerId = "owner",
        title = "Tomato Soup",
        description = "A cozy winter classic",
        notes = "Serve with crusty bread",
        ingredients = listOf("2 tomatoes", "1 cup broth"),
        steps = listOf(RecipeStep("Simmer until fragrant")),
        tags = listOf("soup", "comfort"),
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun matchesTitle() {
        assertTrue(recipe.matchesSearch("tomato"))
    }

    @Test
    fun matchesIngredient() {
        assertTrue(recipe.matchesSearch("broth"))
    }

    @Test
    fun matchesStepText() {
        assertTrue(recipe.matchesSearch("fragrant"))
    }

    @Test
    fun matchesTag() {
        assertTrue(recipe.matchesSearch("comfort"))
    }

    @Test
    fun blankQueryMatchesAll() {
        assertTrue(recipe.matchesSearch(""))
        assertTrue(recipe.matchesSearch("   "))
    }

    @Test
    fun noMatch() {
        assertFalse(recipe.matchesSearch("pizza"))
    }
}
