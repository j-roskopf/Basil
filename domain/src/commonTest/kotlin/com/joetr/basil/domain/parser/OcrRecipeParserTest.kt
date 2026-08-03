package com.joetr.basil.domain.parser

import com.joetr.basil.domain.model.ExtractionConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrRecipeParserTest {
    @Test
    fun parsesTitleIngredientsAndSteps() {
        val text = """
            Tomato Basil Pasta
            Ingredients
            2 cups pasta
            1 tbsp olive oil
            3 cloves garlic
            Instructions
            Boil pasta for 10 minutes.
            Sauté garlic in oil for 3 minutes.
            Toss together and serve.
        """.trimIndent()
        val result = OcrRecipeParser.parse(text)
        assertEquals("Tomato Basil Pasta", result.title)
        assertTrue(result.ingredients.size >= 2)
        assertTrue(result.steps.size >= 2)
        assertEquals(ExtractionConfidence.PARTIAL, result.confidence)
    }

    @Test
    fun skipsPageNumberWhenGuessingTitle() {
        val text = """
            Page 12
            Tomato Basil Pasta
            Ingredients
            2 cups pasta
            Instructions
            Boil pasta for 10 minutes.
        """.trimIndent()
        val result = OcrRecipeParser.parse(text)
        assertEquals("Tomato Basil Pasta", result.title)
    }

    @Test
    fun returnsNoneWhenNoStructure() {
        val result = OcrRecipeParser.parse("Just a random note with no recipe structure.")
        assertEquals(ExtractionConfidence.NONE, result.confidence)
    }
}
