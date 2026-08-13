package com.joetr.basil.data.recipe.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.joetr.basil.data.image.DefaultImageRepository
import com.joetr.basil.platform.isFirebaseStorageImageUrl
import com.joetr.basil.data.image.stageRemoteImageForUpload
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.domain.model.PendingSyncEntry
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.SyncState
import com.joetr.basil.domain.model.SyncStatus
import com.joetr.basil.network.BasilFirebase
import com.joetr.basil.platform.currentTimeMillis
import com.joetr.basil.platform.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public class RecipeSyncService(
    private val database: BasilDatabase,
    private val firebase: BasilFirebase,
    private val imageRepository: DefaultImageRepository? = null,
    private val scope: CoroutineScope? = null,
) {
    private val backend: RecipeSyncBackend = createRecipeSyncBackend(database, firebase)
    private val syncMutex = Mutex()
    private val _state = MutableStateFlow(SyncState(SyncStatus.SYNCED))
    public val state: StateFlow<SyncState> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private var lastSuccessfulSyncAt: Long = 0L

    public suspend fun syncNow(forcePull: Boolean = false) {
        debounceJob?.cancel()
        runSync(RecipeSyncOptions(forcePull = forcePull))
    }

    /**
     * Sync when returning to foreground, but skip if a recent sync succeeded and nothing is queued.
     */
    public suspend fun syncIfStale() {
        if (!firebase.isConfigured) return
        val hasPending = backend.pendingCount() > 0
        if (
            !hasPending &&
            lastSuccessfulSyncAt > 0L &&
            currentTimeMillis() - lastSuccessfulSyncAt < FOREGROUND_SYNC_MIN_INTERVAL_MS
        ) {
            return
        }
        syncNow()
    }

    public suspend fun retryFailed() {
        backend.resetPullCursor()
        syncNow(forcePull = true)
    }

    /** Full pull from cloud after account sign-in (resets pagination cursor first). */
    public suspend fun syncAfterSignIn() {
        debounceJob?.cancel()
        backend.resetPullCursor()
        syncNow(forcePull = true)
    }

    public suspend fun dropPendingSync() {
        backend.dropPending()
        _state.value = SyncState(status = SyncStatus.SYNCED, pendingCount = 0)
    }

    public suspend fun dropPendingSyncEntry(id: String) {
        backend.dropPendingEntry(id)
        refreshState()
    }

    public suspend fun enqueueRecipe(recipe: Recipe) {
        backend.enqueueUpsert(recipe)
        scheduleSync()
    }

    public suspend fun queueRecipe(recipe: Recipe) {
        backend.enqueueUpsert(recipe)
    }

    public suspend fun enqueueDelete(recipeId: String, updatedAt: Long) {
        backend.enqueueDelete(recipeId, updatedAt)
        scheduleSync()
    }

    public suspend fun onLocalWrite() = scheduleSync()

    private suspend fun scheduleSync() {
        val debounceScope = scope
        if (debounceScope == null) {
            syncNow()
            return
        }
        debounceJob?.cancel()
        debounceJob = debounceScope.launch {
            delay(SYNC_DEBOUNCE_MS)
            runSync(RecipeSyncOptions())
        }
    }

    private suspend fun runSync(options: RecipeSyncOptions) {
        if (!firebase.isConfigured) return
        if (!isNetworkAvailable()) {
            refreshState()
            return
        }
        syncMutex.withLock {
            withContext(Dispatchers.Default) {
                if (firebase.currentIdToken().isNullOrBlank()) {
                    refreshState(status = SyncStatus.ERROR, errorMessage = "Not signed in to sync")
                    return@withContext
                }
                _state.value = _state.value.copy(status = SyncStatus.SYNCING)
                runCatching {
                    prepareImagesForSync()
                    val result = backend.sync(options)
                    if (result.rejected > 0) {
                        error(result.errorMessage ?: "Sync rejected ${result.rejected} change(s)")
                    }
                }.onSuccess {
                    lastSuccessfulSyncAt = currentTimeMillis()
                    refreshState()
                }.onFailure { error ->
                    refreshState(
                        status = SyncStatus.ERROR,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    private suspend fun prepareImagesForSync() {
        stageUnhostedRecipeImages()
        uploadPendingImages()
    }

    /** Re-host third-party image URLs in Firebase Storage so they sync across devices. */
    private suspend fun stageUnhostedRecipeImages() {
        val repo = imageRepository ?: return
        val rows = withContext(Dispatchers.Default) {
            database.recipesQueries.selectAllRecipes().awaitAsList()
        }
        for (row in rows) {
            if (row.local_image_id != null) continue
            val imageUrl = row.image_url ?: continue
            if (isFirebaseStorageImageUrl(imageUrl)) continue
            val recipe = Recipe(
                id = row.id,
                ownerId = row.owner_id,
                title = row.title,
                imageUrl = imageUrl,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
            )
            val staged = stageRemoteImageForUpload(repo, recipe)
            if (staged == recipe) continue
            val now = currentTimeMillis()
            withContext(Dispatchers.Default) {
                database.recipesQueries.insertRecipe(
                    id = staged.id,
                    owner_id = staged.ownerId,
                    title = row.title,
                    description = row.description,
                    image_url = staged.imageUrl,
                    local_image_id = staged.localImageId,
                    source_url = row.source_url,
                    servings = row.servings,
                    prep_minutes = row.prep_minutes,
                    cook_minutes = row.cook_minutes,
                    ingredients = row.ingredients,
                    steps = row.steps,
                    tags = row.tags,
                    notes = row.notes,
                    is_favourite = row.is_favourite,
                    created_at = row.created_at,
                    updated_at = now,
                    deleted = row.deleted,
                    pending_sync = 1L,
                )
            }
            backend.enqueueUpsert(staged.copy(updatedAt = now))
        }
    }

    /** Upload local image blobs to Firebase Storage and queue the updated recipe for push. */
    private suspend fun uploadPendingImages() {
        val repo = imageRepository ?: return
        for (recipe in repo.pendingUploadRecipes()) {
            val updated = repo.uploadPendingForRecipe(recipe) ?: continue
            backend.enqueueUpsert(updated)
        }
    }

    private suspend fun refreshState(
        status: SyncStatus? = null,
        errorMessage: String? = null,
    ) {
        val pending = backend.pendingEntries()
        val pendingCount = pending.size
        _state.value = SyncState(
            status = status ?: if (pendingCount > 0) SyncStatus.PENDING else SyncStatus.SYNCED,
            pendingCount = pendingCount,
            errorMessage = errorMessage,
            pendingEntries = pending,
        )
    }

    private companion object {
        const val SYNC_DEBOUNCE_MS: Long = 3_000L
        const val FOREGROUND_SYNC_MIN_INTERVAL_MS: Long = 5 * 60 * 1_000L
    }
}
