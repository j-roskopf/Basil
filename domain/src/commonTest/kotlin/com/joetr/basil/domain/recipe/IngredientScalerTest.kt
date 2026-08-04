package com.joetr.basil.domain.recipe

import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientScalerTest {
    @Test
    fun doublesWholeQuantity() {
        assertEquals("4 cups flour", IngredientScaler.scaleIngredient("2 cups flour", 2.0))
    }

    @Test
    fun scalesGrams() {
        assertEquals("1000 g light spelt flour", IngredientScaler.scaleIngredient("500 g light spelt flour", 2.0))
    }

    @Test
    fun scalesFractions() {
        assertEquals("1 cup sugar", IngredientScaler.scaleIngredient("1/2 cup sugar", 2.0))
    }

    @Test
    fun scalesRanges() {
        assertEquals("2–4 tbsp oil", IngredientScaler.scaleIngredient("1-2 tbsp oil", 2.0))
    }

    @Test
    fun leavesUnparseableLinesUntouched() {
        assertEquals("Salt and pepper", IngredientScaler.scaleIngredient("Salt and pepper", 2.0))
    }

    @Test
    fun scaleIngredientsUsesServingRatio() {
        val original = listOf("2 cups flour", "1 egg")
        val scaled = IngredientScaler.scaleIngredients(original, 4, 8)
        assertEquals(listOf("4 cups flour", "2 egg"), scaled)
    }

    @Test
    fun returnsOriginalWhenServingsMatch() {
        val original = listOf("2 cups flour")
        assertEquals(original, IngredientScaler.scaleIngredients(original, 4, 4))
    }
}
