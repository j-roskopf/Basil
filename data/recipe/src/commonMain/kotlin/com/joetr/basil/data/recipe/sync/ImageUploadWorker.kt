package com.joetr.basil.data.recipe.sync

import com.joetr.basil.data.image.DefaultImageRepository
import com.joetr.basil.domain.repository.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public class ImageUploadWorker(
    private val imageRepository: DefaultImageRepository,
    private val recipeRepository: RecipeRepository,
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
        val pending = imageRepository.pendingUploadRecipes()
        pending.forEach { recipe ->
            val updated = imageRepository.uploadPendingForRecipe(recipe) ?: return@forEach
            recipeRepository.save(updated)
        }
        if (pending.isNotEmpty()) {
            syncService.syncNow()
        }
    }
}
