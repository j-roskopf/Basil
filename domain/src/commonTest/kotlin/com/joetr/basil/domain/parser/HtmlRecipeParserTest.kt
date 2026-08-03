package com.joetr.basil.domain.parser

import com.joetr.basil.domain.model.ExtractionConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlRecipeParserTest {
    @Test
    fun parsesAllRecipesStyleJsonLdArrayWithImageObject() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            [{
              "@context":"http://schema.org",
              "@type":["Recipe","NewsArticle"],
              "name":"Good Old-Fashioned Pancakes",
              "description":"Fluffy pancakes",
              "image":{"@type":"ImageObject","url":"https://example.com/pancakes.jpg"},
              "prepTime":"PT5M",
              "cookTime":"PT15M",
              "recipeYield":"8",
              "recipeIngredient":["1.5 cups flour","1 egg"],
              "recipeInstructions":[
                {"@type":"HowToStep","text":"Mix dry ingredients."},
                {"@type":"HowToStep","text":"Cook 2-3 minutes per side."}
              ]
            }]
            </script>
            </head><body></body></html>
        """.trimIndent()

        val recipe = HtmlRecipeParser.parse(html, "https://www.allrecipes.com/recipe/21014/")
        assertEquals(ExtractionConfidence.FULL, recipe.confidence)
        assertEquals("Good Old-Fashioned Pancakes", recipe.title)
        assertEquals("https://example.com/pancakes.jpg", recipe.imageUrl)
        assertEquals(5, recipe.prepMinutes)
        assertEquals(15, recipe.cookMinutes)
        assertEquals(8, recipe.servings)
        assertEquals(2, recipe.ingredients.size)
        assertEquals(2, recipe.steps.size)
        assertEquals(3, recipe.steps[1].minutes)
    }

    @Test
    fun detectsBotChallengePage() {
        val html = "<html><body>Simple Page Enable JavaScript and cookies to continue</body></html>"
        assertTrue(HtmlRecipeParser.looksLikeBotChallenge(html))
    }

    @Test
    fun parseIsoDuration() {
        assertEquals(5, HtmlRecipeParser.parseIsoDuration("PT5M"))
        assertEquals(90, HtmlRecipeParser.parseIsoDuration("PT1H30M"))
        assertEquals(20, HtmlRecipeParser.parseIsoDuration("P0Y0M0DT0H20M0.000S"))
        assertEquals(45, HtmlRecipeParser.parseIsoDuration("P0Y0M0DT0H45M0.000S"))
        assertEquals(null, HtmlRecipeParser.parseIsoDuration(null))
    }

    @Test
    fun parsesJetpackRecipeCardLikeSmittenKitchen() {
        val html = """
            <html><head>
            <meta property="og:title" content="Everyday Chocolate Cake"/>
            <meta property="og:image" content="https://example.com/cake.jpg"/>
            </head><body>
            <div class="jetpack-recipe h-recipe">
              <h2 class="jetpack-recipe-title p-name">Everyday Chocolate Cake</h2>
              <div class="jetpack-recipe-ingredients">
                <ul>
                  <li class="jetpack-recipe-ingredient">1/2 cup butter</li>
                  <li class="jetpack-recipe-ingredient">1 cup sugar</li>
                </ul>
              </div>
              <div class="jetpack-recipe-directions">
                Heat oven to 325°F.</p>
                <p>Mix butter and sugar.</p>
                <p>Bake 50 minutes.</p>
              </div>
            </div>
            </body></html>
        """.trimIndent()

        val recipe = HtmlRecipeParser.parse(html, "https://smittenkitchen.com/example/")
        assertEquals(ExtractionConfidence.FULL, recipe.confidence)
        assertEquals("Everyday Chocolate Cake", recipe.title)
        assertEquals("https://example.com/cake.jpg", recipe.imageUrl)
        assertEquals(2, recipe.ingredients.size)
        assertTrue(recipe.ingredients[0].contains("butter"))
        assertEquals(3, recipe.steps.size)
        assertTrue(recipe.steps[0].text.contains("325"))
    }

    @Test
    fun decodesHtmlEntitiesInTitle() {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {
              "@type":"Recipe",
              "name":"The Food Lab&#39;s Cookies",
              "recipeIngredient":["butter"],
              "recipeInstructions":[{"@type":"HowToStep","text":"Mix."}]
            }
            </script>
            </head></html>
        """.trimIndent()
        val recipe = HtmlRecipeParser.parse(html)
        assertEquals("The Food Lab's Cookies", recipe.title)
    }
}
