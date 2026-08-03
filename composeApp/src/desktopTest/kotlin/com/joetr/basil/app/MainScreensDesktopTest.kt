package com.joetr.basil.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeQuery
import com.joetr.basil.domain.repository.ImportHistoryEntry
import com.joetr.basil.domain.model.SessionState
import com.joetr.basil.domain.model.SyncState
import com.joetr.basil.domain.model.SyncStatus
import com.joetr.basil.domain.repository.ImportRepository
import com.joetr.basil.domain.repository.RecipeRepository
import com.joetr.basil.domain.repository.SessionRepository
import com.joetr.basil.domain.repository.SyncRepository
import com.joetr.basil.domain.repository.ImageRepository
import com.joetr.basil.domain.usecase.ImportMelaRecipesUseCase
import com.joetr.basil.domain.usecase.ImportRecipeFromUrlUseCase
import com.joetr.basil.domain.usecase.MergeLocalIntoAccountUseCase
import com.joetr.basil.domain.usecase.ObserveImportHistoryUseCase
import com.joetr.basil.domain.usecase.ObserveRecipesUseCase
import com.joetr.basil.domain.usecase.ObserveSessionUseCase
import com.joetr.basil.domain.usecase.ObserveSyncStateUseCase
import com.joetr.basil.domain.model.ThemeMode
import com.joetr.basil.domain.repository.UserSettingsRepository
import com.joetr.basil.domain.usecase.ObserveThemeModeUseCase
import com.joetr.basil.domain.usecase.SetThemeModeUseCase
import com.joetr.basil.domain.usecase.SignOutUseCase
import com.joetr.basil.feature.import.ImportScreen
import com.joetr.basil.feature.import.ImportViewModel
import com.joetr.basil.feature.auth.AuthScreen
import com.joetr.basil.feature.auth.AuthViewModel
import com.joetr.basil.feature.recipes.RecipesScreen
import com.joetr.basil.feature.recipes.RecipesViewModel
import com.joetr.basil.feature.settings.AccountScreen
import com.joetr.basil.feature.settings.SettingsViewModel
import com.joetr.basil.ui.theme.BasilTheme
import io.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

private val roboDir = File("src/desktopTest/resources/roborazzi")

private val fakeImageRepository = object : ImageRepository {
    override suspend fun saveLocalImage(recipeId: String, bytes: ByteArray) = "local-image"
    override suspend fun deleteLocalImage(localImageId: String) = Unit
    override suspend fun fetchAndStageRemoteImage(recipeId: String, remoteUrl: String): String? = null
    override suspend fun readLocalImage(localImageId: String): ByteArray? = null
}

private val fakeImportRepository = object : ImportRepository {
    override suspend fun extractFromUrl(url: String) = error("screenshot")
    override suspend fun extractFromOcrText(text: String) = error("screenshot")
    override fun observeImportHistory() = flowOf(emptyList<ImportHistoryEntry>())
}

private val fakeRecipeRepository = object : RecipeRepository {
    override fun observeRecipes(query: RecipeQuery) = flowOf(emptyList<Recipe>())
    override fun observeRecipe(id: String) = flowOf(null)
    override suspend fun save(recipe: com.joetr.basil.domain.model.Recipe, syncImmediately: Boolean) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun toggleFavourite(id: String) = Unit
    override suspend fun countByOwner(ownerId: String) = 0
    override suspend fun mergeLocalIntoAccount(localOwnerId: String, accountOwnerId: String) = 0
}

private val fakeSessionRepository = object : SessionRepository {
    override fun observeSession() = flowOf(SessionState.Anonymous("guest"))
    override suspend fun ensureSession() = Unit
    override suspend fun resumePendingWebOAuth(): Boolean = false
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun signUpWithEmail(email: String, password: String) = Unit
    override suspend fun resetPassword(email: String) = Unit
    override suspend fun signInWithGoogle() = Unit
    override suspend fun signOut() = Unit
    override suspend fun needsMergePrompt() = false to 0
    override suspend fun acceptMerge() = 0
    override suspend fun declineMerge() = Unit
}

private val fakeUserSettingsRepository = object : UserSettingsRepository {
    override fun observeThemeMode() = flowOf(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) = Unit
}

private val fakeSyncRepository = object : SyncRepository {
    override fun observeSyncState() = flowOf(SyncState(SyncStatus.SYNCED))
    override suspend fun syncNow() = Unit
    override suspend fun syncAfterSignIn() = Unit
    override suspend fun retryFailed() = Unit
    override suspend fun dropPendingSync() = Unit
    override suspend fun dropPendingSyncEntry(id: String) = Unit
}

class MainScreensDesktopTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recipesScreenWidths() = captureAtWidths("Recipes") {
        RecipesScreen(
            RecipesViewModel(ObserveRecipesUseCase(fakeRecipeRepository)),
            onRecipeClick = {},
            onAddRecipe = {},
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun importScreenWidths() = captureAtWidths("Import") {
        ImportScreen(
            ImportViewModel(
                ImportRecipeFromUrlUseCase(fakeImportRepository),
                ImportMelaRecipesUseCase(
                    fakeRecipeRepository,
                    fakeImageRepository,
                    fakeSyncRepository,
                    ObserveSessionUseCase(fakeSessionRepository),
                ),
                ObserveImportHistoryUseCase(fakeImportRepository),
            ),
            onExtracted = {},
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accountScreenWidths() = captureAtWidths("Account") {
        AccountScreen(
            SettingsViewModel(
                ObserveSessionUseCase(fakeSessionRepository),
                ObserveSyncStateUseCase(fakeSyncRepository),
                fakeSyncRepository,
                ObserveThemeModeUseCase(fakeUserSettingsRepository),
                SetThemeModeUseCase(fakeUserSettingsRepository),
                SignOutUseCase(fakeSessionRepository),
            ),
            onSignIn = {},
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun authScreenWidths() = captureAtWidths("Auth") {
        AuthScreen(
            viewModel = AuthViewModel(
                sessionRepository = fakeSessionRepository,
                mergeUseCase = MergeLocalIntoAccountUseCase(fakeSessionRepository),
            ),
            onDone = {},
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun captureAtWidths(screen: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        listOf(390, 720, 1200).forEach { width ->
            runDesktopComposeUiTest {
                setContent {
                    BasilTheme(darkTheme = false) {
                        Box(Modifier.width(width.dp).fillMaxSize()) {
                            content()
                        }
                    }
                }
                onRoot().captureRoboImage(roboDir.resolve("${screen}_${width}.png").absolutePath)
            }
        }
    }
}
