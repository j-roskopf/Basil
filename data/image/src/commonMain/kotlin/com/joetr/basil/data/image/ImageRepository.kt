package com.joetr.basil.data.image

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.repository.ImageRepository
import com.joetr.basil.network.BasilFirebase
import com.joetr.basil.platform.currentTimeMillis
import com.joetr.basil.platform.resizeImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class DefaultImageRepository(
    private val database: BasilDatabase,
    private val httpClient: HttpClient,
    private val firebase: BasilFirebase,
) : ImageRepository {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveLocalImage(recipeId: String, bytes: ByteArray): String {
        val id = Uuid.random().toString()
        val resized = resizeImage(bytes, maxLongEdge = 1600, quality = 80)
        withContext(Dispatchers.Default) {
            database.recipesQueries.insertImageBlob(
                id = id,
                recipe_id = recipeId,
                data_ = resized,
                created_at = currentTimeMillis(),
            )
        }
        return id
    }

    override suspend fun deleteLocalImage(localImageId: String) {
        withContext(Dispatchers.Default) {
            database.recipesQueries.deleteImageBlob(localImageId)
        }
    }

    override suspend fun fetchAndStageRemoteImage(recipeId: String, remoteUrl: String): String? =
        runCatching {
            val bytes = httpClient.get(remoteUrl).readRawBytes()
            saveLocalImage(recipeId, bytes)
        }.getOrNull()

    override suspend fun readLocalImage(localImageId: String): ByteArray? =
        withContext(Dispatchers.Default) {
            database.recipesQueries.selectImageBlob(localImageId).awaitAsOneOrNull()
        }

    public suspend fun uploadPendingForRecipe(recipe: Recipe): Recipe? {
        val localImageId = recipe.localImageId ?: return null
        if (!recipe.imageUrl.isNullOrBlank() || !firebase.isConfigured) return null
        val bytes = readLocalImage(localImageId) ?: return null
        val session = firebase.currentSession() ?: return null
        return runCatching {
            val downloadUrl = firebase.storage.uploadJpeg(
                idToken = session.idToken,
                ownerId = recipe.ownerId.ifBlank { session.localId },
                recipeId = recipe.id,
                bytes = bytes,
            )
            val now = currentTimeMillis()
            withContext(Dispatchers.Default) {
                database.recipesQueries.updateRecipeImageUrl(downloadUrl, now, recipe.id)
                database.recipesQueries.deleteImageBlob(localImageId)
            }
            recipe.copy(imageUrl = downloadUrl, localImageId = null, updatedAt = now)
        }.getOrNull()
    }

    public suspend fun pendingUploadRecipes(): List<Recipe> =
        withContext(Dispatchers.Default) {
            database.recipesQueries.selectRecipesWithPendingImages().awaitAsList()
        }.map { row ->
            Recipe(
                id = row.id,
                ownerId = row.owner_id,
                title = row.title,
                imageUrl = row.image_url,
                localImageId = row.local_image_id,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
            )
        }
}
