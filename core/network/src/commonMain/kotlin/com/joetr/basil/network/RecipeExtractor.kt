package com.joetr.basil.network

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.ExtractionConfidence
import com.joetr.basil.domain.parser.HtmlRecipeParser
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

@SingleIn(AppScope::class)
@Inject
public class RecipeExtractor(
    private val firebase: BasilFirebase,
) {
    public suspend fun extract(url: String): ExtractedRecipe {
        if (!firebase.isConfigured) {
            error("Configure Firebase (FIREBASE_WEB_API_KEY / FIREBASE_PROJECT_ID) to enable URL extraction.")
        }
        val token = firebase.currentIdToken()
            ?: error("Sign in anonymously before importing a recipe.")

        val remote = runCatching { firebase.functions.extractRecipe(token, url) }.getOrNull()
        if (remote != null && hasRecipeBody(remote) && !isChallengeStub(remote)) {
            return remote
        }

        // Cloud fetch is often bot-blocked (AllRecipes, etc.). Retry from the device IP.
        val local = extractOnDevice(url)
        if (local != null && hasRecipeBody(local)) return local

        if (remote != null && isChallengeStub(remote)) {
            error(
                "This site blocked automatic import.",
            )
        }
        if (remote != null) return remote
        error("Couldn't extract a recipe from that page.")
    }

    private suspend fun extractOnDevice(url: String): ExtractedRecipe? {
        val html = runCatching {
            firebase.httpClient.get(url) {
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                )
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            }.bodyAsText()
        }.getOrNull() ?: return null

        if (HtmlRecipeParser.looksLikeBotChallenge(html)) return null
        return HtmlRecipeParser.parse(html, url)
    }

    private fun hasRecipeBody(recipe: ExtractedRecipe): Boolean =
        recipe.ingredients.isNotEmpty() || recipe.steps.isNotEmpty()

    private fun isChallengeStub(recipe: ExtractedRecipe): Boolean {
        val raw = recipe.rawText.orEmpty()
        return recipe.confidence == ExtractionConfidence.NONE &&
            (
                HtmlRecipeParser.looksLikeBotChallenge(raw) ||
                    raw.contains("Enable JavaScript and cookies", ignoreCase = true) ||
                    recipe.title.equals("Simple Page", ignoreCase = true)
                )
    }
}
