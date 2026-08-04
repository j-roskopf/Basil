package com.joetr.basil.di

import com.joetr.basil.data.auth.createFirebaseSessionStore
import com.joetr.basil.data.auth.DefaultSessionRepository
import com.joetr.basil.data.image.DefaultImageRepository
import com.joetr.basil.data.recipe.DefaultUserSettingsRepository
import com.joetr.basil.data.recipe.DefaultImportRepository
import com.joetr.basil.data.recipe.DefaultRecipeRepository
import com.joetr.basil.data.recipe.DefaultSyncRepository
import com.joetr.basil.data.recipe.sync.ImageUploadWorker
import com.joetr.basil.data.recipe.sync.RecipeSyncService
import com.joetr.basil.database.BasilDataLayer
import com.joetr.basil.domain.usecase.DeleteRecipeUseCase
import com.joetr.basil.domain.usecase.ExportRecipesUseCase
import com.joetr.basil.domain.usecase.ImportBasilRecipesUseCase
import com.joetr.basil.domain.usecase.ImportMelaRecipesUseCase
import com.joetr.basil.domain.usecase.ImportRecipeFromUrlUseCase
import com.joetr.basil.domain.usecase.MergeLocalIntoAccountUseCase
import com.joetr.basil.domain.usecase.ObserveImportHistoryUseCase
import com.joetr.basil.domain.usecase.ObserveRecipeUseCase
import com.joetr.basil.domain.usecase.ObserveRecipesUseCase
import com.joetr.basil.domain.usecase.ObserveSessionUseCase
import com.joetr.basil.domain.usecase.ObserveSyncStateUseCase
import com.joetr.basil.domain.usecase.ObserveThemeModeUseCase
import com.joetr.basil.domain.usecase.SaveRecipeUseCase
import com.joetr.basil.domain.usecase.ScanRecipeFromImageUseCase
import com.joetr.basil.domain.usecase.SetThemeModeUseCase
import com.joetr.basil.domain.usecase.SignOutUseCase
import com.joetr.basil.domain.usecase.ToggleFavouriteUseCase
import com.joetr.basil.feature.auth.AuthViewModel
import com.joetr.basil.feature.cook.CookViewModel
import com.joetr.basil.feature.editor.EditorViewModel
import com.joetr.basil.feature.import.ImportViewModel
import com.joetr.basil.feature.recipes.RecipeDetailViewModel
import com.joetr.basil.feature.recipes.RecipesViewModel
import com.joetr.basil.feature.scan.ScanViewModel
import com.joetr.basil.feature.settings.SettingsViewModel
import com.joetr.basil.network.BasilFirebase
import com.joetr.basil.network.RecipeExtractor
import com.joetr.basil.network.createBasilHttpClient
import com.joetr.basil.network.createBasilImageHttpClient
import com.joetr.basil.platform.AppLifecycleObserver
import com.joetr.basil.updates.createAppUpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

public class AppGraph(
    dataLayer: BasilDataLayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val httpClient = createBasilHttpClient()
    public val imageHttpClient = createBasilImageHttpClient()
    private val firebase = BasilFirebase.create(
        httpClient = httpClient,
        sessionStore = createFirebaseSessionStore(dataLayer.database),
    )
    private val imageRepository = DefaultImageRepository(dataLayer.database, imageHttpClient, firebase)
    private val syncService = RecipeSyncService(dataLayer.database, firebase, imageRepository, scope)
    private val recipeRepository = DefaultRecipeRepository(dataLayer.database, syncService, imageRepository)
    private val syncRepository = DefaultSyncRepository(syncService)
    private val sessionRepository = DefaultSessionRepository(
        dataLayer.database,
        recipeRepository,
        firebase,
        syncRepository,
    )
    private val recipeExtractor = RecipeExtractor(firebase)
    private val importRepository = DefaultImportRepository(dataLayer.database, recipeExtractor)
    public val imageRepositoryPublic: DefaultImageRepository get() = imageRepository
    private val imageUploadWorker = ImageUploadWorker(imageRepository, syncService, scope)
    private val userSettingsRepository = DefaultUserSettingsRepository(dataLayer.database, scope)
    public val updates = createAppUpdateService(scope, httpClient)

    private val startupJob = scope.launch {
        firebase.preloadPersistedSession()
        val signedInViaOAuth = sessionRepository.resumePendingWebOAuth()
        sessionRepository.ensureSession()
        if (signedInViaOAuth) {
            syncRepository.syncAfterSignIn()
        } else {
            syncService.syncNow()
        }
        imageUploadWorker.start()
        updates.checkForUpdates()
    }

    init {
        AppLifecycleObserver.onForeground = {
            scope.launch { syncService.syncIfStale() }
        }
    }

    internal suspend fun awaitStartup() {
        startupJob.join()
    }

    public val recipesViewModel: RecipesViewModel = RecipesViewModel(ObserveRecipesUseCase(recipeRepository))
    public val recipeDetailViewModel: RecipeDetailViewModel = RecipeDetailViewModel(
        observeRecipe = ObserveRecipeUseCase(recipeRepository),
        deleteRecipe = DeleteRecipeUseCase(recipeRepository),
        toggleFavouriteUseCase = ToggleFavouriteUseCase(recipeRepository),
    )
    public val importViewModel: ImportViewModel = ImportViewModel(
        ImportRecipeFromUrlUseCase(importRepository),
        ImportMelaRecipesUseCase(recipeRepository, imageRepository, syncRepository, ObserveSessionUseCase(sessionRepository)),
        ImportBasilRecipesUseCase(recipeRepository, imageRepository, syncRepository, ObserveSessionUseCase(sessionRepository)),
        ExportRecipesUseCase(recipeRepository, imageRepository, ObserveSessionUseCase(sessionRepository)),
        ObserveImportHistoryUseCase(importRepository),
    )
    public val editorViewModel: EditorViewModel = EditorViewModel(
        saveRecipe = SaveRecipeUseCase(recipeRepository),
        observeSession = ObserveSessionUseCase(sessionRepository),
        observeRecipe = ObserveRecipeUseCase(recipeRepository),
        imageRepository = imageRepository,
    )
    public val cookViewModel: CookViewModel = CookViewModel(ObserveRecipeUseCase(recipeRepository))
    public val authViewModel: AuthViewModel = AuthViewModel(
        sessionRepository,
        MergeLocalIntoAccountUseCase(sessionRepository),
    )
    public val scanViewModel: ScanViewModel = ScanViewModel(
        ScanRecipeFromImageUseCase(importRepository),
        imageRepository,
    )
    public val settingsViewModel: SettingsViewModel = SettingsViewModel(
        ObserveSessionUseCase(sessionRepository),
        ObserveSyncStateUseCase(syncRepository),
        syncRepository,
        ObserveThemeModeUseCase(userSettingsRepository),
        SetThemeModeUseCase(userSettingsRepository),
        SignOutUseCase(sessionRepository),
        updates,
    )

}

public suspend fun createBasilAppGraph(): AppGraph {
    val dataLayer = com.joetr.basil.database.createBasilDataLayer()
    val graph = AppGraph(dataLayer)
    graph.awaitStartup()
    return graph
}
