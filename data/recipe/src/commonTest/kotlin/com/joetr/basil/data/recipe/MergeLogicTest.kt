package com.joetr.basil.data.recipe

import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeLogicTest {
    @Test
    fun dedupesBySourceUrlWhenMerging() {
        val existing = Recipe(
            id = "a",
            ownerId = "account",
            title = "Pasta",
            sourceUrl = "https://example.com/pasta",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val incoming = Recipe(
            id = "b",
            ownerId = "local",
            title = "Pasta copy",
            sourceUrl = "https://example.com/pasta",
            createdAt = 2L,
            updatedAt = 2L,
        )
        val merged = listOf(existing).filterNot { row ->
            incoming.sourceUrl != null && row.sourceUrl == incoming.sourceUrl
        } + incoming.copy(ownerId = "account")
        assertEquals(1, merged.size)
        assertEquals("account", merged.single().ownerId)
    }

    @Test
    fun assignsNewIdsWhenMergingDistinctRecipes() {
        val incoming = Recipe(
            id = "local-1",
            ownerId = "local",
            title = "Soup",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val reassigned = incoming.copy(id = "new-id", ownerId = "account")
        assertEquals("new-id", reassigned.id)
        assertEquals("account", reassigned.ownerId)
    }
}
