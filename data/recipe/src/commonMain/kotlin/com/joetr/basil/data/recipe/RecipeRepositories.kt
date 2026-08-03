package com.joetr.basil.data.recipe

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.db.Recipes
import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.ExtractionConfidence
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeQuery
import com.joetr.basil.domain.model.RecipeSort
import com.joetr.basil.domain.model.RecipeStep
import com.joetr.basil.domain.model.matchesSearch
import com.joetr.basil.data.image.stageRemoteImageForUpload
import com.joetr.basil.data.recipe.sync.RecipeSyncService
import com.joetr.basil.domain.parser.OcrRecipeParser
import com.joetr.basil.domain.repository.ImageRepository
import com.joetr.basil.domain.repository.ImportHistoryEntry
import com.joetr.basil.domain.repository.ImportRepository
import com.joetr.basil.domain.repository.RecipeRepository
import com.joetr.basil.network.RecipeExtractor
import com.joetr.basil.platform.currentTimeMillis
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class DefaultRecipeRepository(
    private val database: BasilDatabase,
    private val syncService: RecipeSyncService? = null,
    private val imageRepository: ImageRepository? = null,
) : RecipeRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeRecipes(query: RecipeQuery): Flow<List<Recipe>> =
        database.recipesQueries.selectAllRecipes()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { it.toDomain(json) }
                    .filter { recipe ->
                        recipe.matchesSearch(query.search) &&
                            (query.tags.isEmpty() || query.tags.all { it in recipe.tags }) &&
                            (!query.favouritesOnly || recipe.isFavourite)
                    }
                    .sortedWith(
                        when (query.sort) {
                            RecipeSort.TITLE_ASC -> compareBy { it.title.lowercase() }
                            RecipeSort.CREATED_DESC -> compareByDescending { it.createdAt }
                            RecipeSort.UPDATED_DESC -> compareByDescending { it.updatedAt }
                        },
                    )
            }

    override fun observeRecipe(id: String): Flow<Recipe?> =
        database.recipesQueries.selectRecipeById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain(json) }

    override suspend fun save(recipe: Recipe, syncImmediately: Boolean) {
        val prepared = imageRepository?.let { stageRemoteImageForUpload(it, recipe) } ?: recipe
        val now = currentTimeMillis()
        val updatedAt = if (prepared.updatedAt == 0L) now else prepared.updatedAt
        withContext(Dispatchers.Default) {
            database.recipesQueries.insertRecipe(
                id = prepared.id,
                owner_id = prepared.ownerId,
                title = prepared.title,
                description = prepared.description,
                image_url = prepared.imageUrl,
                local_image_id = prepared.localImageId,
                source_url = prepared.sourceUrl,
                servings = prepared.servings?.toLong(),
                prep_minutes = prepared.prepMinutes?.toLong(),
                cook_minutes = prepared.cookMinutes?.toLong(),
                ingredients = json.encodeToString(prepared.ingredients),
                steps = json.encodeToString(prepared.steps),
                tags = json.encodeToString(prepared.tags),
                notes = prepared.notes,
                is_favourite = if (prepared.isFavourite) 1L else 0L,
                created_at = prepared.createdAt,
                updated_at = updatedAt,
                deleted = if (prepared.deleted) 1L else 0L,
                pending_sync = 1L,
            )
        }
        val preparedRecipe = prepared.copy(updatedAt = updatedAt)
        if (syncImmediately) {
            syncService?.enqueueRecipe(preparedRecipe)
        } else {
            syncService?.queueRecipe(preparedRecipe)
        }
    }

    override suspend fun delete(id: String) {
        val now = currentTimeMillis()
        withContext(Dispatchers.Default) {
            database.recipesQueries.softDeleteRecipe(now, id)
        }
        syncService?.enqueueDelete(id, now)
    }

    override suspend fun toggleFavourite(id: String) {
        val current = withContext(Dispatchers.Default) {
            database.recipesQueries.selectRecipeById(id).awaitAsOneOrNull()
        } ?: return
        val domain = current.toDomain(json)
        save(domain.copy(isFavourite = !domain.isFavourite))
    }

    override suspend fun countByOwner(ownerId: String): Int =
        withContext(Dispatchers.Default) {
            database.recipesQueries.countRecipesByOwner(ownerId).awaitAsOne().toInt()
        }

    override suspend fun getAllByOwner(ownerId: String): List<Recipe> =
        withContext(Dispatchers.Default) {
            database.recipesQueries.selectRecipesByOwner(ownerId)
                .awaitAsList()
                .map { it.toDomain(json) }
        }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun mergeLocalIntoAccount(localOwnerId: String, accountOwnerId: String): Int {
        val localRecipes = withContext(Dispatchers.Default) {
            database.recipesQueries.selectRecipesByOwner(localOwnerId).awaitAsList()
        }
        var merged = 0
        localRecipes.forEach { row ->
            val recipe = row.toDomain(json)
            val newId = Uuid.random().toString()
            save(recipe.copy(id = newId, ownerId = accountOwnerId, updatedAt = 0L), syncImmediately = false)
            merged++
        }
        withContext(Dispatchers.Default) {
            database.recipesQueries.updateOwnerId(accountOwnerId, localOwnerId)
        }
        syncService?.syncNow()
        return merged
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class DefaultImportRepository(
    private val database: BasilDatabase,
    private val extractor: RecipeExtractor,
) : ImportRepository {
    override suspend fun extractFromUrl(url: String): ExtractedRecipe {
        val result = extractor.extract(url)
        if (result.ingredients.isEmpty() && result.steps.isEmpty()) {
            error(
                result.rawText
                    ?.takeIf { it.contains("blocked", ignoreCase = true) || it.contains("JavaScript", ignoreCase = true) }
                    ?: "Couldn't extract a recipe from that page.",
            )
        }
        withContext(Dispatchers.Default) {
            database.recipesQueries.insertImportHistory(
                url = url,
                title = result.title,
                confidence = result.confidence.name,
                imported_at = currentTimeMillis(),
            )
        }
        return result
    }

    override suspend fun extractFromOcrText(text: String): ExtractedRecipe = OcrRecipeParser.parse(text)

    override fun observeImportHistory(): Flow<List<ImportHistoryEntry>> =
        database.recipesQueries.selectImportHistory()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map {
                    ImportHistoryEntry(
                        url = it.url,
                        title = it.title,
                        confidence = ExtractionConfidence.valueOf(it.confidence),
                        importedAt = it.imported_at,
                    )
                }
            }
}

internal fun Recipes.toDomain(json: Json): Recipe = Recipe(
    id = id,
    ownerId = owner_id,
    title = title,
    description = description,
    imageUrl = image_url,
    localImageId = local_image_id,
    sourceUrl = source_url,
    servings = servings?.toInt(),
    prepMinutes = prep_minutes?.toInt(),
    cookMinutes = cook_minutes?.toInt(),
    ingredients = runCatching { json.decodeFromString<List<String>>(ingredients) }.getOrDefault(emptyList()),
    steps = runCatching { json.decodeFromString<List<RecipeStep>>(steps) }.getOrDefault(emptyList()),
    tags = runCatching { json.decodeFromString<List<String>>(tags) }.getOrDefault(emptyList()),
    notes = notes,
    isFavourite = is_favourite == 1L,
    createdAt = created_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)
