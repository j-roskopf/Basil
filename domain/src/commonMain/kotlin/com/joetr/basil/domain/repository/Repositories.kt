package com.joetr.basil.domain.repository

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeQuery
import com.joetr.basil.domain.model.SessionState
import com.joetr.basil.domain.model.SharedRecipe
import com.joetr.basil.domain.model.SharedRecipeLink
import com.joetr.basil.domain.model.SyncState
import com.joetr.basil.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

public interface RecipeRepository {
    public fun observeRecipes(query: RecipeQuery): Flow<List<Recipe>>
    public fun observeRecipe(id: String): Flow<Recipe?>
    public suspend fun save(recipe: Recipe, syncImmediately: Boolean = true)
    public suspend fun delete(id: String)
    public suspend fun toggleFavourite(id: String)
    public suspend fun countByOwner(ownerId: String): Int
    public suspend fun getAllByOwner(ownerId: String): List<Recipe>
    public suspend fun mergeLocalIntoAccount(localOwnerId: String, accountOwnerId: String): Int
}

public interface SharedRecipeRepository {
    public suspend fun create(recipe: Recipe): SharedRecipeLink
    public suspend fun get(token: String): SharedRecipe
    public suspend fun revoke(token: String)
}

public interface ImportRepository {
    public suspend fun extractFromUrl(url: String): ExtractedRecipe
    public suspend fun extractFromOcrText(text: String): ExtractedRecipe
    public fun observeImportHistory(): Flow<List<ImportHistoryEntry>>
}

public data class ImportHistoryEntry(
    val url: String,
    val title: String?,
    val confidence: com.joetr.basil.domain.model.ExtractionConfidence,
    val importedAt: Long,
)

public interface SessionRepository {
    public fun observeSession(): Flow<SessionState>
    public suspend fun ensureSession()
    public suspend fun resumePendingWebOAuth(): Boolean
    public suspend fun signInWithEmail(email: String, password: String)
    public suspend fun signUpWithEmail(email: String, password: String)
    public suspend fun resetPassword(email: String)
    public suspend fun signInWithGoogle()
    public suspend fun signOut()
    public suspend fun needsMergePrompt(): Pair<Boolean, Int>
    public suspend fun acceptMerge(): Int
    public suspend fun declineMerge()
}

public interface SyncRepository {
    public fun observeSyncState(): Flow<SyncState>
    public suspend fun syncNow()
    public suspend fun syncAfterSignIn()
    public suspend fun retryFailed()
    public suspend fun dropPendingSync()
    public suspend fun dropPendingSyncEntry(id: String)
}

public interface ImageRepository {
    public suspend fun saveLocalImage(recipeId: String, bytes: ByteArray): String
    public suspend fun deleteLocalImage(localImageId: String)
    public suspend fun fetchAndStageRemoteImage(recipeId: String, remoteUrl: String): String?
    public suspend fun readLocalImage(localImageId: String): ByteArray?
}

public interface UserSettingsRepository {
    public fun observeThemeMode(): Flow<ThemeMode>
    public suspend fun setThemeMode(mode: ThemeMode)
    public fun observeShowStoreSearchLinks(): Flow<Boolean>
    public suspend fun setShowStoreSearchLinks(enabled: Boolean)
}
