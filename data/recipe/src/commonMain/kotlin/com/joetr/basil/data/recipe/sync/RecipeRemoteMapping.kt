package com.joetr.basil.data.recipe.sync

import com.joetr.basil.db.Recipes
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import com.joetr.basil.network.RemoteRecipeRow
import com.joetr.basil.platform.currentTimeMillis
import kotlinx.serialization.json.Json

private val recipeJson = Json { ignoreUnknownKeys = true }

internal fun Recipes.toRemoteRow(): RemoteRecipeRow = RemoteRecipeRow(
    title = title,
    description = description,
    imageUrl = image_url,
    sourceUrl = source_url,
    servings = servings?.toInt(),
    prepMinutes = prep_minutes?.toInt(),
    cookMinutes = cook_minutes?.toInt(),
    ingredients = runCatching { recipeJson.decodeFromString<List<String>>(ingredients) }.getOrDefault(emptyList()),
    steps = runCatching { recipeJson.decodeFromString<List<RecipeStep>>(steps) }.getOrDefault(emptyList()),
    tags = runCatching { recipeJson.decodeFromString<List<String>>(tags) }.getOrDefault(emptyList()),
    notes = notes,
    isFavourite = is_favourite == 1L,
    createdAt = created_at,
    updatedAt = if (updated_at == 0L) currentTimeMillis() else updated_at,
    deleted = deleted == 1L,
)

internal fun Recipe.toRemoteRow(): RemoteRecipeRow = RemoteRecipeRow(
    title = title,
    description = description,
    imageUrl = imageUrl,
    sourceUrl = sourceUrl,
    servings = servings,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    ingredients = ingredients,
    steps = steps,
    tags = tags,
    notes = notes,
    isFavourite = isFavourite,
    createdAt = createdAt,
    updatedAt = if (updatedAt == 0L) currentTimeMillis() else updatedAt,
    deleted = deleted,
)
