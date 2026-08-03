package com.joetr.basil.network

import com.joetr.basil.domain.model.RecipeStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Firestore document fields for `users/{uid}/recipes/{recipeId}` (camelCase). */
@Serializable
public data class RemoteRecipeRow(
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val isFavourite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)

public val remoteRecipeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
