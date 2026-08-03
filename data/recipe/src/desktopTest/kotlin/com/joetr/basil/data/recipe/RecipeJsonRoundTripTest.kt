package com.joetr.basil.data.recipe

import com.joetr.basil.domain.model.RecipeStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class RecipeJsonRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun ingredientsAndStepsRoundTrip() {
        val ingredients = listOf("1 cup tomatoes", "2 tbsp butter")
        val steps = listOf(RecipeStep("Cook for 10 minutes.", minutes = 10))

        val ingredientsJson = json.encodeToString(ingredients)
        val stepsJson = json.encodeToString(steps)

        assertEquals(
            listOf("1 cup tomatoes", "2 tbsp butter"),
            json.decodeFromString<List<String>>(ingredientsJson),
        )
        assertEquals(
            listOf(RecipeStep("Cook for 10 minutes.", minutes = 10)),
            json.decodeFromString<List<RecipeStep>>(stepsJson),
        )
    }
}
