package com.joetr.basil.data.image

import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.repository.ImageRepository

private const val FIREBASE_STORAGE_HOST = "firebasestorage.googleapis.com"

/** True when the URL is already hosted in our Firebase Storage bucket. */
public fun isFirebaseStorageImageUrl(imageUrl: String?): Boolean =
    imageUrl?.contains(FIREBASE_STORAGE_HOST) == true

/**
 * Downloads a third-party image into local storage so [DefaultImageRepository.uploadPendingForRecipe]
 * can re-host it in Firebase Storage and sync the URL to other devices.
 */
public suspend fun stageRemoteImageForUpload(
    imageRepository: ImageRepository,
    recipe: Recipe,
): Recipe {
    if (recipe.localImageId != null) return recipe
    val remoteUrl = recipe.imageUrl ?: return recipe
    if (isFirebaseStorageImageUrl(remoteUrl)) return recipe
    val localId = imageRepository.fetchAndStageRemoteImage(recipe.id, remoteUrl) ?: return recipe
    return recipe.copy(localImageId = localId, imageUrl = null)
}
