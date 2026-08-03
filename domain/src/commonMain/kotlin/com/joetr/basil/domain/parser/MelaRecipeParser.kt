package com.joetr.basil.domain.parser

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.ExtractionConfidence
import com.joetr.basil.domain.model.RecipeStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses Mela [.melarecipe](https://mela.recipes/fileformat/index.html) JSON and
 * [.melarecipes](https://mela.recipes/fileformat/index.html) ZIP archives.
 */
public object MelaRecipeParser {
    private val json = Json { ignoreUnknownKeys = true }

    public data class ImportItem(
        val extracted: ExtractedRecipe,
        val imageBytes: ByteArray? = null,
        val isFavourite: Boolean = false,
        val notes: String? = null,
    )

    public fun parseArchive(bytes: ByteArray): List<ImportItem> {
        if (bytes.size >= 4 &&
            bytes[0].toInt() == 0x50 && bytes[1].toInt() == 0x4B &&
            (bytes[2].toInt() == 0x03 || bytes[2].toInt() == 0x05 || bytes[2].toInt() == 0x07)
        ) {
            return MelaArchiveReader.readRecipeJsonEntries(bytes)
                .mapNotNull { entryBytes -> parseRecipeJson(entryBytes) }
        }
        return parseRecipeJson(bytes)?.let { listOf(it) }.orEmpty()
    }

    public fun parseRecipeJson(bytes: ByteArray): ImportItem? {
        val raw = bytes.decodeToString().trim()
        if (raw.isEmpty()) return null
        val mela = runCatching { json.decodeFromString<MelaRecipeJson>(raw) }.getOrNull() ?: return null
        return mela.toImportItem()
    }

    @Serializable
    private data class MelaRecipeJson(
        val id: String? = null,
        val title: String? = null,
        val text: String? = null,
        val images: List<String> = emptyList(),
        val categories: List<String> = emptyList(),
        val yield: String? = null,
        val prepTime: String? = null,
        val cookTime: String? = null,
        val totalTime: String? = null,
        val ingredients: String? = null,
        val instructions: String? = null,
        val notes: String? = null,
        val nutrition: String? = null,
        val link: String? = null,
        val favorite: Boolean = false,
        @SerialName("wantToCook") val wantToCook: Boolean = false,
    ) {
        fun toImportItem(): ImportItem? {
            val title = title?.trim().takeUnless { it.isNullOrBlank() }
            val ingredientsList = splitLines(ingredients)
            val steps = splitLines(instructions).map { line ->
                RecipeStep(text = line, minutes = HtmlRecipeParser.parseStepMinutes(line))
            }
            if (title == null && ingredientsList.isEmpty() && steps.isEmpty()) {
                val hasText = text?.trim()?.isNotBlank() == true
                val hasLink = link?.trim()?.isNotBlank() == true
                if (!hasText && !hasLink) return null
            }

            val notesParts = buildList {
                notes?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                nutrition?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }

            val confidence = when {
                ingredientsList.isNotEmpty() && steps.isNotEmpty() -> ExtractionConfidence.FULL
                ingredientsList.isNotEmpty() || steps.isNotEmpty() || title != null -> ExtractionConfidence.PARTIAL
                else -> ExtractionConfidence.NONE
            }

            val imageBytes = images.firstOrNull { it.isNotBlank() }?.let(::decodeBase64Image)

            return ImportItem(
                extracted = ExtractedRecipe(
                    confidence = confidence,
                    title = title,
                    description = text?.trim()?.takeUnless { it.isBlank() },
                    sourceUrl = link?.trim()?.takeUnless { it.isBlank() },
                    servings = parseYield(yield),
                    prepMinutes = parseMelaDuration(prepTime) ?: parseMelaDuration(totalTime),
                    cookMinutes = parseMelaDuration(cookTime),
                    ingredients = ingredientsList,
                    steps = steps,
                    tags = categories.map { it.trim() }.filter { it.isNotBlank() },
                ),
                imageBytes = imageBytes,
                isFavourite = favorite,
                notes = notesParts.joinToString("\n\n").takeUnless { it.isBlank() },
            )
        }
    }

    private fun splitLines(value: String?): List<String> =
        value.orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun parseYield(value: String?): Int? =
        value?.trim()?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** Mela uses human-readable strings like "5min" or ISO-8601 durations. */
    public fun parseMelaDuration(value: String?): Int? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        HtmlRecipeParser.parseIsoDuration(trimmed)?.let { return it }
        val hours = Regex("""(\d+)\s*h(?:ours?)?""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("""(\d+)\s*(?:min(?:ute)?s?|m)\b""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + minutes
        return total.takeIf { it > 0 }
    }

    private fun decodeBase64Image(value: String): ByteArray? {
        val trimmed = value.trim()
        val normalized = trimmed.substringAfter("base64,", trimmed)
            .replace("\n", "")
            .replace("\r", "")
        if (normalized.isEmpty()) return null
        return runCatching { kotlin.io.encoding.Base64.decode(normalized) }.getOrNull()
    }
}
