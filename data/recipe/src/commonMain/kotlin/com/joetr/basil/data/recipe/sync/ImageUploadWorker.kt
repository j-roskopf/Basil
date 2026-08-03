package com.joetr.basil.data.recipe.sync

import com.joetr.basil.data.image.DefaultImageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public class ImageUploadWorker(
    private val imageRepository: DefaultImageRepository,
    private val syncService: RecipeSyncService,
    private val scope: CoroutineScope,
) {
    public fun start() {
        scope.launch {
            while (isActive) {
                runCatching { processPending() }
                delay(15_000)
            }
        }
    }

    public suspend fun processPending() {
        var uploaded = false
        imageRepository.pendingUploadRecipes().forEach { recipe ->
            val updated = imageRepository.uploadPendingForRecipe(recipe) ?: return@forEach
            // uploadPendingForRecipe already updates image_url in the DB; queue a sync push
            // without re-saving a partial Recipe (that would wipe ingredients/steps).
            syncService.queueRecipe(updated)
            uploaded = true
        }
        if (uploaded) {
            syncService.syncNow()
        }
    }
}
