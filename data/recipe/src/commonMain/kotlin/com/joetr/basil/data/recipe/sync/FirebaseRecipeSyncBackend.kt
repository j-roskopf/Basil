package com.joetr.basil.data.recipe.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.domain.model.PendingSyncEntry
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.network.BasilFirebase
import com.joetr.basil.network.FirebaseSession
import com.joetr.basil.network.FirestoreRecipeDocument
import com.joetr.basil.network.firestoreErrorDetail
import com.joetr.basil.network.isFirestoreNotFound
import com.joetr.basil.network.isFirestoreUnauthenticated
import com.joetr.basil.network.isNetworkConnectivityError
import com.joetr.basil.platform.isNetworkAvailable
import com.joetr.basil.db.Recipes
import com.joetr.basil.network.remoteRecipeJson
import com.joetr.basil.platform.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString

internal class FirebaseRecipeSyncBackend(
    private val database: BasilDatabase,
    private val firebase: BasilFirebase,
) : RecipeSyncBackend {
    override suspend fun sync(options: RecipeSyncOptions): RecipeSyncRunResult {
        val session = firebase.sessionForSync() ?: return RecipeSyncRunResult(0, 0, 0)
        return runCatching { syncWithSession(session, options) }.getOrElse { error ->
            if (!error.isFirestoreUnauthenticated()) throw error
            if (!isNetworkAvailable()) throw error
            val refreshed = runCatching { firebase.refreshSession(session) }.getOrElse { refreshError ->
                if (refreshError.isNetworkConnectivityError()) throw error
                throw refreshError
            }
            syncWithSession(refreshed, options)
        }
    }

    private suspend fun syncWithSession(session: FirebaseSession, options: RecipeSyncOptions): RecipeSyncRunResult {
        val hadOutboxAtStart = pendingCount() > 0
        // 1. Push deletes so remote tombstones exist before we reconcile from cloud.
        val deletePush = pushDeletes(session.localId, session.idToken)
        // 2. Pull remote changes when not pushing a batch of local edits (avoids re-reading own writes).
        val pulled = if (options.forcePull || !hadOutboxAtStart) {
            pull(session.localId, session.idToken)
        } else {
            0
        }
        // 3. Push remaining upserts after pull may have cleared stale local entries.
        val upsertPush = pushUpserts(session.localId, session.idToken)
        return RecipeSyncRunResult(
            pulled = pulled,
            pushed = deletePush.pushed + upsertPush.pushed,
            rejected = deletePush.rejected + upsertPush.rejected,
            errorMessage = deletePush.errorMessage ?: upsertPush.errorMessage,
        )
    }

    override suspend fun resetPullCursor() {
        withContext(Dispatchers.Default) {
            database.recipesQueries.upsertSetting(PULL_CURSOR_UPDATED_AT, "")
            database.recipesQueries.upsertSetting(PULL_CURSOR_ID, "")
        }
    }

    override suspend fun enqueueUpsert(recipe: Recipe) {
        withContext(Dispatchers.Default) {
            database.recipesQueries.enqueueOutbox(recipe.id, "UPSERT", currentTimeMillis())
            database.recipesQueries.markPendingSync(currentTimeMillis(), recipe.id)
        }
    }

    override suspend fun enqueueDelete(recipeId: String, updatedAt: Long) {
        withContext(Dispatchers.Default) {
            database.recipesQueries.enqueueOutbox(recipeId, "DELETE", currentTimeMillis())
            database.recipesQueries.markPendingSync(updatedAt, recipeId)
        }
    }

    override suspend fun pendingCount(): Int = pendingEntries().size

    override suspend fun pendingEntries(): List<PendingSyncEntry> =
        withContext(Dispatchers.Default) {
            database.recipesQueries.selectOutbox().awaitAsList().map { entry ->
                val title = database.recipesQueries.selectRecipeByIdAny(entry.recipe_id)
                    .awaitAsOneOrNull()?.title
                    ?: entry.recipe_id
                PendingSyncEntry(
                    id = entry.recipe_id,
                    title = title,
                    kind = entry.kind,
                )
            }
        }

    override suspend fun dropPending() {
        withContext(Dispatchers.Default) {
            database.recipesQueries.deleteAllOutbox()
            database.recipesQueries.selectPendingSync().awaitAsList().forEach {
                database.recipesQueries.clearPendingSync(it.id)
            }
        }
    }

    override suspend fun dropPendingEntry(id: String) {
        withContext(Dispatchers.Default) {
            database.recipesQueries.deleteOutboxEntry(id)
            database.recipesQueries.clearPendingSync(id)
        }
    }

    private suspend fun pull(ownerId: String, idToken: String): Int {
        var cursorUpdatedAt = withContext(Dispatchers.Default) {
            database.recipesQueries.selectSetting(PULL_CURSOR_UPDATED_AT).awaitAsOneOrNull()?.toLongOrNull()
        }
        var cursorId = withContext(Dispatchers.Default) {
            database.recipesQueries.selectSetting(PULL_CURSOR_ID).awaitAsOneOrNull()
        }
        var pulled = 0
        var hasMore = true
        while (hasMore) {
            yield()
            val page = firebase.firestore.pullRecipes(
                idToken = idToken,
                ownerId = ownerId,
                cursorUpdatedAt = cursorUpdatedAt,
                cursorId = cursorId,
            )
            if (page.isEmpty()) {
                hasMore = false
            } else {
                page.forEach { mergeRemote(ownerId, it) }
                val last = page.last()
                cursorUpdatedAt = last.recipe.updatedAt
                cursorId = last.id
                withContext(Dispatchers.Default) {
                    database.recipesQueries.upsertSetting(PULL_CURSOR_UPDATED_AT, cursorUpdatedAt.toString())
                    database.recipesQueries.upsertSetting(PULL_CURSOR_ID, cursorId)
                }
                pulled += page.size
                hasMore = page.size >= 100
            }
        }
        return pulled
    }

    private suspend fun pushDeletes(ownerId: String, idToken: String): PushResult {
        val outbox = withContext(Dispatchers.Default) {
            database.recipesQueries.selectOutbox().awaitAsList()
        }
        val deleteIds = mutableListOf<String>()
        val orphanedDeletes = mutableListOf<String>()

        for (entry in outbox) {
            if (entry.kind != "DELETE") continue
            val row = withContext(Dispatchers.Default) {
                database.recipesQueries.selectRecipeByIdAny(entry.recipe_id).awaitAsOneOrNull()
            }
            if (row == null) {
                orphanedDeletes += entry.recipe_id
            } else {
                deleteIds += entry.recipe_id
            }
        }

        if (orphanedDeletes.isNotEmpty()) {
            clearOutboxEntries(orphanedDeletes)
        }
        if (deleteIds.isEmpty()) return PushResult()

        return pushDeletesToCloud(idToken, ownerId, deleteIds)
    }

    private suspend fun pushUpserts(ownerId: String, idToken: String): PushResult {
        val outbox = withContext(Dispatchers.Default) {
            database.recipesQueries.selectOutbox().awaitAsList()
        }
        if (outbox.isEmpty()) return PushResult()

        val upsertDocs = mutableListOf<FirestoreRecipeDocument>()
        val upsertIds = mutableListOf<String>()
        val staleUpserts = mutableListOf<String>()

        for (entry in outbox) {
            if (entry.kind == "DELETE") continue
            val row = withContext(Dispatchers.Default) {
                database.recipesQueries.selectRecipeByIdAny(entry.recipe_id).awaitAsOneOrNull()
            }
            if (row == null) continue
            if (row.deleted == 1L) {
                staleUpserts += row.id
                continue
            }
            upsertDocs += FirestoreRecipeDocument(id = row.id, recipe = row.toRemoteRow())
            upsertIds += row.id
        }

        if (staleUpserts.isNotEmpty()) {
            clearOutboxEntries(staleUpserts)
        }
        if (upsertDocs.isEmpty()) return PushResult()

        return try {
            firebase.firestore.upsertRecipes(idToken, ownerId, upsertDocs)
            clearOutboxEntries(upsertIds)
            advancePullCursor(upsertIds)
            PushResult(pushed = upsertIds.size)
        } catch (error: Throwable) {
            PushResult(
                rejected = upsertIds.size,
                errorMessage = error.firestoreErrorDetail(),
            )
        }
    }

    private suspend fun pushDeletesToCloud(
        idToken: String,
        ownerId: String,
        recipeIds: List<String>,
    ): PushResult {
        try {
            firebase.firestore.markRecipesDeleted(idToken, ownerId, recipeIds)
            clearOutboxEntries(recipeIds)
            advancePullCursor(recipeIds)
            return PushResult(pushed = recipeIds.size)
        } catch (_: Throwable) {
            // Fall back to per-recipe deletes so a missing cloud doc doesn't block the batch.
        }

        var pushed = 0
        var rejected = 0
        var errorMessage: String? = null
        for (id in recipeIds) {
            try {
                firebase.firestore.markRecipesDeleted(idToken, ownerId, listOf(id))
                clearOutboxEntries(listOf(id))
                advancePullCursor(listOf(id))
                pushed++
            } catch (error: Throwable) {
                if (error.isFirestoreNotFound()) {
                    // Recipe never made it to the cloud — nothing to delete remotely.
                    clearOutboxEntries(listOf(id))
                    pushed++
                } else {
                    rejected++
                    errorMessage = error.firestoreErrorDetail()
                }
            }
        }
        return PushResult(pushed = pushed, rejected = rejected, errorMessage = errorMessage)
    }

    private suspend fun clearOutboxEntries(ids: List<String>) {
        withContext(Dispatchers.Default) {
            ids.forEach { id ->
                database.recipesQueries.deleteOutboxEntry(id)
                database.recipesQueries.clearPendingSync(id)
            }
        }
    }

    /** Move pull cursor past documents we just pushed so the next pull does not re-read them. */
    private suspend fun advancePullCursor(recipeIds: List<String>) {
        if (recipeIds.isEmpty()) return
        val rows = withContext(Dispatchers.Default) {
            recipeIds.mapNotNull { id ->
                database.recipesQueries.selectRecipeByIdAny(id).awaitAsOneOrNull()
            }
        }
        val cursorRow = rows.maxWithOrNull(compareBy<Recipes> { it.updated_at }.thenBy { it.id })
        if (cursorRow == null) return
        val cursorUpdatedAt = maxOf(cursorRow.updated_at, currentTimeMillis())
        withContext(Dispatchers.Default) {
            database.recipesQueries.upsertSetting(PULL_CURSOR_UPDATED_AT, cursorUpdatedAt.toString())
            database.recipesQueries.upsertSetting(PULL_CURSOR_ID, cursorRow.id)
        }
    }

    private suspend fun mergeRemote(ownerId: String, doc: FirestoreRecipeDocument) {
        withContext(Dispatchers.Default) {
            val pending = database.recipesQueries.selectOutboxEntry(doc.id).awaitAsOneOrNull()
            val local = database.recipesQueries.selectRecipeByIdAny(doc.id).awaitAsOneOrNull()
            when (remoteMergeAction(pending?.kind, local?.updated_at, doc.recipe)) {
                RemoteMergeAction.Skip -> return@withContext
                RemoteMergeAction.ApplyAndClearOutbox -> {
                    database.recipesQueries.deleteOutboxEntry(doc.id)
                    database.recipesQueries.clearPendingSync(doc.id)
                }
                RemoteMergeAction.Apply -> Unit
            }
            applyRemoteRecipe(ownerId, doc, local)
        }
    }

    private suspend fun applyRemoteRecipe(
        ownerId: String,
        doc: FirestoreRecipeDocument,
        local: Recipes?,
    ) {
        val remote = doc.recipe
        database.recipesQueries.insertRecipe(
            id = doc.id,
            owner_id = ownerId,
            title = remote.title,
            description = remote.description,
            image_url = remote.imageUrl,
            local_image_id = local?.local_image_id,
            source_url = remote.sourceUrl,
            servings = remote.servings?.toLong(),
            prep_minutes = remote.prepMinutes?.toLong(),
            cook_minutes = remote.cookMinutes?.toLong(),
            ingredients = remoteRecipeJson.encodeToString(remote.ingredients),
            steps = remoteRecipeJson.encodeToString(remote.steps),
            tags = remoteRecipeJson.encodeToString(remote.tags),
            notes = remote.notes,
            is_favourite = if (remote.isFavourite) 1L else 0L,
            created_at = remote.createdAt,
            updated_at = remote.updatedAt,
            deleted = if (remote.deleted) 1L else 0L,
            pending_sync = 0L,
        )
    }

    private companion object {
        const val PULL_CURSOR_UPDATED_AT = "firestore_pull_cursor_updated_at"
        const val PULL_CURSOR_ID = "firestore_pull_cursor_id"
    }
}

private data class PushResult(
    val pushed: Int = 0,
    val rejected: Int = 0,
    val errorMessage: String? = null,
)
