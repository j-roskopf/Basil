package com.joetr.basil.domain.export

import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Portable Basil recipe backup format (`.basilrecipes` JSON file).
 */
public object BasilRecipeCodec {
    public const val FORMAT_VERSION: Int = 1
    public const val FILE_EXTENSION: String = "basilrecipes"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Serializable
    public data class ExportFile(
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAt: Long,
        val recipes: List<RecipeEntry> = emptyList(),
    )

    @Serializable
    public data class RecipeEntry(
        val title: String,
        val description: String? = null,
        val imageUrl: String? = null,
        val imageBase64: String? = null,
        val sourceUrl: String? = null,
        val servings: Int? = null,
        val prepMinutes: Int? = null,
        val cookMinutes: Int? = null,
        val ingredients: List<String> = emptyList(),
        val steps: List<RecipeStep> = emptyList(),
        val tags: List<String> = emptyList(),
        val notes: String? = null,
        val isFavourite: Boolean = false,
        val createdAt: Long? = null,
    )

    public data class PreparedExport(
        val bytes: ByteArray,
        val recipeCount: Int,
    )

    public data class ParsedRecipe(
        val entry: RecipeEntry,
        val imageBytes: ByteArray?,
    )

    @OptIn(ExperimentalEncodingApi::class)
    public fun export(
        recipes: List<Recipe>,
        imageBytesByLocalId: Map<String, ByteArray>,
        exportedAt: Long,
    ): PreparedExport {
        val entries = recipes.map { recipe ->
            val imageBytes = recipe.localImageId?.let { imageBytesByLocalId[it] }
            RecipeEntry(
                title = recipe.title,
                description = recipe.description,
                imageUrl = recipe.imageUrl,
                imageBase64 = imageBytes?.let { Base64.encode(it) },
                sourceUrl = recipe.sourceUrl,
                servings = recipe.servings,
                prepMinutes = recipe.prepMinutes,
                cookMinutes = recipe.cookMinutes,
                ingredients = recipe.ingredients,
                steps = recipe.steps,
                tags = recipe.tags,
                notes = recipe.notes,
                isFavourite = recipe.isFavourite,
                createdAt = recipe.createdAt,
            )
        }
        val file = ExportFile(
            exportedAt = exportedAt,
            recipes = entries,
        )
        return PreparedExport(
            bytes = json.encodeToString(ExportFile.serializer(), file).encodeToByteArray(),
            recipeCount = entries.size,
        )
    }

    public fun parse(bytes: ByteArray): List<ParsedRecipe> {
        val raw = bytes.decodeToString().trim()
        if (raw.isEmpty()) error("Backup file is empty.")
        val file = runCatching {
            json.decodeFromString(ExportFile.serializer(), raw)
        }.getOrElse { error("Not a valid Basil recipe backup.") }
        if (file.formatVersion != FORMAT_VERSION) {
            error("Unsupported backup format version ${file.formatVersion}.")
        }
        if (file.recipes.isEmpty()) error("No recipes found in this backup.")
        return file.recipes.map { entry ->
            ParsedRecipe(
                entry = entry,
                imageBytes = entry.imageBase64?.let(::decodeBase64Image),
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64Image(value: String): ByteArray? {
        val normalized = value.trim()
            .substringAfter("base64,", value.trim())
            .replace("\n", "")
            .replace("\r", "")
        if (normalized.isEmpty()) return null
        return runCatching { Base64.decode(normalized) }.getOrNull()
    }
}
