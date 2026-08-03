package com.joetr.basil.domain.model

import kotlinx.serialization.Serializable

@Serializable
public data class RecipeStep(
    val text: String,
    val minutes: Int? = null,
)

public data class Recipe(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val localImageId: String? = null,
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
    val updatedAt: Long,
    val deleted: Boolean = false,
)

public enum class RecipeSource {
    MANUAL,
    IMPORT,
    SCAN,
}

@Serializable
public enum class ExtractionConfidence {
    FULL,
    PARTIAL,
    NONE,
}

@Serializable
public data class ExtractedRecipe(
    val confidence: ExtractionConfidence,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val localImageId: String? = null,
    val sourceUrl: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tags: List<String> = emptyList(),
    val rawText: String? = null,
)

public enum class RecipeSort {
    UPDATED_DESC,
    TITLE_ASC,
    CREATED_DESC,
}

public data class RecipeQuery(
    val search: String = "",
    val tags: Set<String> = emptySet(),
    val sort: RecipeSort = RecipeSort.UPDATED_DESC,
    val favouritesOnly: Boolean = false,
)

public sealed interface SessionState {
    public data class LocalPending(val deviceOwnerId: String) : SessionState
    public data class Anonymous(val userId: String) : SessionState
    public data class Authenticated(val userId: String, val email: String?) : SessionState
}

public enum class SyncStatus {
    SYNCED,
    SYNCING,
    PENDING,
    ERROR,
}

public data class PendingSyncEntry(
    val id: String,
    val title: String,
    val kind: String,
)

public data class SyncState(
    val status: SyncStatus,
    val pendingCount: Int = 0,
    val errorMessage: String? = null,
    val pendingEntries: List<PendingSyncEntry> = emptyList(),
)
