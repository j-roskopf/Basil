package com.joetr.basil.data.recipe.sync

import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.domain.model.PendingSyncEntry
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.network.BasilFirebase

internal data class RecipeSyncRunResult(
    val pulled: Int,
    val pushed: Int,
    val rejected: Int,
    val errorMessage: String? = null,
)

internal data class RecipeSyncOptions(
    /** When true, always pull from cloud (e.g. after sign-in or manual retry). */
    val forcePull: Boolean = false,
)

internal interface RecipeSyncBackend {
    suspend fun sync(options: RecipeSyncOptions = RecipeSyncOptions()): RecipeSyncRunResult
    suspend fun resetPullCursor()
    suspend fun enqueueUpsert(recipe: Recipe)
    suspend fun enqueueDelete(recipeId: String, updatedAt: Long)
    suspend fun pendingCount(): Int
    suspend fun pendingEntries(): List<PendingSyncEntry>
    suspend fun dropPending()
    suspend fun dropPendingEntry(id: String)
}

internal fun createRecipeSyncBackend(
    database: BasilDatabase,
    firebase: BasilFirebase,
): RecipeSyncBackend = FirebaseRecipeSyncBackend(database, firebase)
