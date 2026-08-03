package com.joetr.basil.domain.usecase

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeQuery
import com.joetr.basil.domain.model.SessionState
import com.joetr.basil.domain.model.SyncState
import com.joetr.basil.domain.model.ThemeMode
import com.joetr.basil.domain.parser.MelaRecipeParser
import com.joetr.basil.domain.repository.ImageRepository
import com.joetr.basil.domain.repository.ImportRepository
import com.joetr.basil.domain.repository.RecipeRepository
import com.joetr.basil.domain.repository.SessionRepository
import com.joetr.basil.domain.repository.SyncRepository
import com.joetr.basil.domain.repository.UserSettingsRepository
import com.joetr.basil.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public class ObserveImportHistoryUseCase(
    private val importRepository: ImportRepository,
) {
    public operator fun invoke() = importRepository.observeImportHistory()
}

public class ObserveRecipesUseCase(
    private val repository: RecipeRepository,
) {
    public operator fun invoke(query: RecipeQuery): Flow<List<Recipe>> = repository.observeRecipes(query)
}

public class ObserveRecipeUseCase(
    private val repository: RecipeRepository,
) {
    public operator fun invoke(id: String): Flow<Recipe?> = repository.observeRecipe(id)
}

public class SaveRecipeUseCase(
    private val repository: RecipeRepository,
) {
    public suspend operator fun invoke(recipe: Recipe) {
        repository.save(recipe)
    }
}

public class DeleteRecipeUseCase(
    private val repository: RecipeRepository,
) {
    public suspend operator fun invoke(id: String) {
        repository.delete(id)
    }
}

public class ToggleFavouriteUseCase(
    private val repository: RecipeRepository,
) {
    public suspend operator fun invoke(id: String) = repository.toggleFavourite(id)
}

public class ImportRecipeFromUrlUseCase(
    private val importRepository: ImportRepository,
) {
    public suspend operator fun invoke(url: String): ExtractedRecipe = importRepository.extractFromUrl(url)
}

public class ScanRecipeFromImageUseCase(
    private val importRepository: ImportRepository,
) {
    public suspend operator fun invoke(ocrText: String): ExtractedRecipe = importRepository.extractFromOcrText(ocrText)
}

public class ImportMelaRecipesUseCase(
    private val recipeRepository: RecipeRepository,
    private val imageRepository: ImageRepository,
    private val syncRepository: SyncRepository,
    private val observeSession: ObserveSessionUseCase,
) {
    public data class Result(
        val parsed: Int,
        val saved: Int,
        val failed: Int,
    )

  @OptIn(ExperimentalUuidApi::class)
  public suspend operator fun invoke(archiveBytes: ByteArray): Result {
    val items = MelaRecipeParser.parseArchive(archiveBytes)
    if (items.isEmpty()) error("No recipes found in this Mela export.")

    val ownerId = when (val session = observeSession().first()) {
      is SessionState.Authenticated -> session.userId
      is SessionState.Anonymous -> session.userId
      is SessionState.LocalPending -> session.deviceOwnerId
    }
    val now = currentTimeMillis()
    var saved = 0
    var failed = 0

    items.forEach { item ->
      val outcome = runCatching {
        val recipeId = Uuid.random().toString()
        val localImageId = item.imageBytes?.let { bytes ->
          runCatching { imageRepository.saveLocalImage(recipeId, bytes) }.getOrNull()
        }
        val extracted = item.extracted
        recipeRepository.save(
          Recipe(
            id = recipeId,
            ownerId = ownerId,
            title = extracted.title?.trim().takeUnless { it.isNullOrBlank() } ?: "Untitled recipe",
            description = extracted.description,
            imageUrl = extracted.imageUrl,
            localImageId = localImageId,
            sourceUrl = extracted.sourceUrl,
            servings = extracted.servings,
            prepMinutes = extracted.prepMinutes,
            cookMinutes = extracted.cookMinutes,
            ingredients = extracted.ingredients,
            steps = extracted.steps,
            tags = extracted.tags,
            notes = item.notes,
            isFavourite = item.isFavourite,
            createdAt = now,
            updatedAt = 0L,
          ),
          syncImmediately = false,
        )
      }
      if (outcome.isSuccess) saved++ else failed++
    }
    syncRepository.syncNow()
    return Result(parsed = items.size, saved = saved, failed = failed)
  }
}

public class SyncNowUseCase(
    private val syncRepository: SyncRepository,
) {
    public suspend operator fun invoke() = syncRepository.syncNow()
}

public class ObserveSessionUseCase(
    private val sessionRepository: SessionRepository,
) {
    public operator fun invoke(): Flow<SessionState> = sessionRepository.observeSession()
}

public class ObserveSyncStateUseCase(
    private val syncRepository: SyncRepository,
) {
    public operator fun invoke(): Flow<SyncState> = syncRepository.observeSyncState()
}

public class MergeLocalIntoAccountUseCase(
    private val sessionRepository: SessionRepository,
) {
    public suspend fun needsPrompt(): Pair<Boolean, Int> = sessionRepository.needsMergePrompt()
    public suspend fun accept(): Int = sessionRepository.acceptMerge()
    public suspend fun decline() = sessionRepository.declineMerge()
}

public class ObserveThemeModeUseCase(
    private val userSettingsRepository: UserSettingsRepository,
) {
    public operator fun invoke(): Flow<ThemeMode> = userSettingsRepository.observeThemeMode()
}

public class SetThemeModeUseCase(
    private val userSettingsRepository: UserSettingsRepository,
) {
    public suspend operator fun invoke(mode: ThemeMode) = userSettingsRepository.setThemeMode(mode)
}

public class SignOutUseCase(
    private val sessionRepository: SessionRepository,
) {
    public suspend operator fun invoke() = sessionRepository.signOut()
}
