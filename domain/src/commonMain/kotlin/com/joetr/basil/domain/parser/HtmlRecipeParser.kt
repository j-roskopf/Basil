package com.joetr.basil.domain.parser

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.ExtractionConfidence
import com.joetr.basil.domain.model.RecipeStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client-side HTML recipe extraction (JSON-LD first).
 * Used when the cloud fetcher is bot-blocked (common for AllRecipes, NYT, etc.).
 */
public object HtmlRecipeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val ldJsonRegex = Regex(
        pattern = """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
        option = RegexOption.IGNORE_CASE,
    )

    public fun looksLikeBotChallenge(html: String): Boolean {
        val sample = html.take(4_000)
        return sample.contains("Enable JavaScript and cookies to continue", ignoreCase = true) ||
            sample.contains("cf-browser-verification", ignoreCase = true) ||
            sample.contains("Attention Required! | Cloudflare", ignoreCase = true) ||
            (sample.contains("captcha", ignoreCase = true) && sample.length < 8_000)
    }

    public fun parse(html: String, sourceUrl: String = ""): ExtractedRecipe {
        val jsonLd = extractJsonLd(html)
        val card = extractRecipeCard(html)
        val structured = bestPartial(jsonLd, card)
        val ingredients = structured?.ingredients.orEmpty()
        val steps = structured?.steps.orEmpty()
        val title = decodeHtmlEntities(
            structured?.title
                ?: meta(html, "og:title")
                ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.trim(),
        )
        val imageUrl = structured?.imageUrl ?: meta(html, "og:image")
        val confidence = when {
            ingredients.isNotEmpty() && steps.isNotEmpty() -> ExtractionConfidence.FULL
            ingredients.isNotEmpty() || steps.isNotEmpty() -> ExtractionConfidence.PARTIAL
            else -> ExtractionConfidence.NONE
        }
        return ExtractedRecipe(
            confidence = confidence,
            title = title,
            description = structured?.description?.let(::decodeHtmlEntities),
            imageUrl = imageUrl,
            sourceUrl = sourceUrl.ifBlank { null },
            servings = structured?.servings,
            prepMinutes = structured?.prepMinutes,
            cookMinutes = structured?.cookMinutes,
            ingredients = ingredients.map { decodeHtmlEntitiesOrEmpty(it) },
            steps = steps.map { it.copy(text = decodeHtmlEntitiesOrEmpty(it.text)) },
            tags = emptyList(),
            rawText = html
                .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(20_000),
        )
    }

    private data class Partial(
        val title: String? = null,
        val description: String? = null,
        val imageUrl: String? = null,
        val servings: Int? = null,
        val prepMinutes: Int? = null,
        val cookMinutes: Int? = null,
        val ingredients: List<String> = emptyList(),
        val steps: List<RecipeStep> = emptyList(),
    )

    private fun extractJsonLd(html: String): Partial? {
        for (match in ldJsonRegex.findAll(html)) {
            val body = match.groupValues[1].trim()
            if (body.isBlank()) continue
            runCatching {
                val element = json.parseToJsonElement(body)
                val recipe = findRecipeNode(element) ?: return@runCatching null
                return recipeToPartial(recipe)
            }
        }
        return null
    }

    private fun findRecipeNode(element: JsonElement): JsonObject? {
        when (element) {
            is JsonArray -> {
                for (child in element) {
                    findRecipeNode(child)?.let { return it }
                }
            }
            is JsonObject -> {
                if (isRecipeType(element["@type"])) return element
                element["@graph"]?.let { graph ->
                    findRecipeNode(graph)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun isRecipeType(type: JsonElement?): Boolean = when (type) {
        is JsonPrimitive -> type.contentOrNull == "Recipe"
        is JsonArray -> type.any { it is JsonPrimitive && it.contentOrNull == "Recipe" }
        else -> false
    }

    private fun recipeToPartial(recipe: JsonObject): Partial {
        val ingredients = recipe["recipeIngredient"].asStringList()
        val steps = mutableListOf<RecipeStep>()
        when (val instructions = recipe["recipeInstructions"]) {
            is JsonArray -> instructions.forEach { appendInstruction(it, steps) }
            is JsonPrimitive -> instructions.contentOrNull?.let {
                steps += RecipeStep(it, parseStepMinutes(it))
            }
            is JsonObject -> appendInstruction(instructions, steps)
            else -> Unit
        }
        return Partial(
            title = recipe.string("name") ?: recipe.string("headline"),
            description = recipe.string("description"),
            imageUrl = resolveImageUrl(recipe["image"]),
            servings = parseYield(recipe["recipeYield"]),
            prepMinutes = parseIsoDuration(recipe.string("prepTime")),
            cookMinutes = parseIsoDuration(recipe.string("cookTime")),
            ingredients = ingredients,
            steps = steps,
        )
    }

    private fun appendInstruction(element: JsonElement, out: MutableList<RecipeStep>) {
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                out += RecipeStep(it, parseStepMinutes(it))
            }
            is JsonObject -> {
                val type = element["@type"]
                val isSection = when (type) {
                    is JsonPrimitive -> type.contentOrNull == "HowToSection"
                    is JsonArray -> type.any { it is JsonPrimitive && it.contentOrNull == "HowToSection" }
                    else -> false
                }
                if (isSection) {
                    element["itemListElement"]?.jsonArrayOrNull()?.forEach { appendInstruction(it, out) }
                } else {
                    val text = element.string("text") ?: element.string("name")
                    if (!text.isNullOrBlank()) out += RecipeStep(text, parseStepMinutes(text))
                    element["itemListElement"]?.jsonArrayOrNull()?.forEach { appendInstruction(it, out) }
                }
            }
            is JsonArray -> element.forEach { appendInstruction(it, out) }
        }
    }

    private fun resolveImageUrl(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonPrimitive -> element.contentOrNull?.takeUnless { it == "[object Object]" }
        is JsonArray -> element.firstNotNullOfOrNull { resolveImageUrl(it) }
        is JsonObject -> element.string("url") ?: element.string("contentUrl") ?: resolveImageUrl(element["image"])
    }

    private fun parseYield(element: JsonElement?): Int? = when (element) {
        is JsonPrimitive -> {
            val raw = element.contentOrNull ?: return null
            Regex("""(\d+)""").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        }
        is JsonArray -> element.firstNotNullOfOrNull { parseYield(it) }
        else -> null
    }

    /** Parse ISO-8601 durations like PT15M, P0DT20M, and Food Network P0Y0M0DT0H20M0.000S. */
    public fun parseIsoDuration(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val match = Regex(
            """^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(value.trim()) ?: return null
        val years = match.groupValues[1].toIntOrNull() ?: 0
        val months = match.groupValues[2].toIntOrNull() ?: 0
        val days = match.groupValues[3].toIntOrNull() ?: 0
        val hours = match.groupValues[4].toIntOrNull() ?: 0
        val minutes = match.groupValues[5].toIntOrNull() ?: 0
        val seconds = match.groupValues[6].toDoubleOrNull() ?: 0.0
        val total =
            years * 365 * 24 * 60 +
                months * 30 * 24 * 60 +
                days * 24 * 60 +
                hours * 60 +
                minutes +
                if (seconds >= 30) 1 else 0
        return total.takeIf { it > 0 }
    }

    /** Jetpack / h-recipe style cards (Smitten Kitchen, etc.). */
    private fun extractRecipeCard(html: String): Partial? {
        val ingredientRegex = Regex(
            """<(?:li|span|div)[^>]+(?:itemprop=["']recipeIngredient["']|jetpack-recipe-ingredient|p-ingredient)[^>]*>([\s\S]*?)</(?:li|span|div)>""",
            RegexOption.IGNORE_CASE,
        )
        val ingredients = ingredientRegex.findAll(html)
            .map { stripTags(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()

        val directionRegex = Regex(
            """<(?:div|section)[^>]+(?:jetpack-recipe-directions|e-instructions|recipe-directions|recipe__instructions)[^>]*>([\s\S]*?)</(?:div|section)>""",
            RegexOption.IGNORE_CASE,
        )
        val steps = directionRegex.findAll(html)
            .flatMap { stepsFromProseHtml(it.groupValues[1]) }
            .toList()

        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val title = Regex(
            """<(?:h1|h2|h3)[^>]+(?:jetpack-recipe-title|p-name)[^>]*>([\s\S]*?)</(?:h1|h2|h3)>""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)?.let(::stripTags)
            ?: Regex(
                """<[^>]+itemprop=["']name["'][^>]*>([\s\S]*?)</[^>]+>""",
                RegexOption.IGNORE_CASE,
            ).find(html)?.groupValues?.get(1)?.let(::stripTags)

        return Partial(
            title = title,
            ingredients = ingredients,
            steps = steps,
        )
    }

    private fun stepsFromProseHtml(blockHtml: String): List<RecipeStep> {
        var normalized = blockHtml.trim()
        if (normalized.isNotEmpty() &&
            !Regex("""^<p[\s>]""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) &&
            Regex("""</p>""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        ) {
            normalized = "<p>$normalized"
        }
        val paragraphs = Regex("""<p\b[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { stripTags(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()
        if (paragraphs.isNotEmpty()) {
            return paragraphs.map { RecipeStep(it, parseStepMinutes(it)) }
        }
        val text = stripTags(blockHtml)
        if (text.isBlank()) return emptyList()
        return listOf(RecipeStep(text, parseStepMinutes(text)))
    }

    private fun bestPartial(vararg candidates: Partial?): Partial? {
        var best: Partial? = null
        var bestScore = -1
        for (candidate in candidates) {
            if (candidate == null) continue
            val score =
                candidate.ingredients.size * 2 +
                    candidate.steps.size * 2 +
                    (if (candidate.title != null) 1 else 0) +
                    (if (candidate.imageUrl != null) 1 else 0)
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    private fun stripTags(html: String): String =
        decodeHtmlEntitiesOrEmpty(
            html
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim(),
        )

    private fun decodeHtmlEntities(value: String?): String? =
        value?.let(::decodeHtmlEntitiesOrEmpty)

    private fun decodeHtmlEntitiesOrEmpty(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }

    public fun parseStepMinutes(text: String): Int? {
        if (Regex("overnight", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        Regex("""(\d+)\s*(?:-|–|to)\s*(\d+)\s*(?:min(?:ute)?s?|m)\b""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(2)?.toIntOrNull()?.let { return it }
        Regex("""(\d+)\s*(?:hr|hour|hours|h)\b""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it * 60 }
        if (Regex("""half\s+an?\s+hour""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return 30
        if (Regex("""an?\s+hour\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return 60
        return Regex("""(\d+)\s*(?:min(?:ute)?s?|m)\b""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun meta(html: String, prop: String): String? {
        val og = Regex(
            """<meta[^>]+property=["']${Regex.escape(prop)}["'][^>]+content=(["'])(.*?)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.get(2)
        if (og != null) return og
        return Regex(
            """<meta[^>]+name=["']${Regex.escape(prop)}["'][^>]+content=(["'])(.*?)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.get(2)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement?.asStringList(): List<String> = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        is JsonPrimitive -> contentOrNull?.takeIf(String::isNotBlank)?.let { listOf(it) }.orEmpty()
        else -> emptyList()
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
}
