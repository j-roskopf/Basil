package com.joetr.basil.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.joetr.basil.app.basilImages
import com.joetr.basil.di.AppGraph
import com.joetr.basil.feature.auth.AuthScreen
import com.joetr.basil.feature.cook.CookScreen
import com.joetr.basil.feature.editor.EditorScreen
import com.joetr.basil.feature.import.ImportScreen
import com.joetr.basil.feature.recipes.RecipeDetailScreen
import com.joetr.basil.feature.recipes.RecipesTabContent
import com.joetr.basil.feature.settings.AccountScreen
import com.joetr.basil.feature.scan.ScanScreen
import com.joetr.basil.feature.scan.ScanViewModel
import com.joetr.basil.navigation.AccountKey
import com.joetr.basil.navigation.AuthKey
import com.joetr.basil.navigation.CookKey
import com.joetr.basil.navigation.EditorKey
import com.joetr.basil.navigation.ImportKey
import com.joetr.basil.navigation.RecipeDetailKey
import com.joetr.basil.navigation.ScanKey
import com.joetr.basil.navigation.RecipesKey
import com.joetr.basil.domain.model.SessionState
import com.joetr.basil.domain.model.ThemeMode
import com.joetr.basil.navigation.TopLevelDestination
import com.joetr.basil.navigation.AdaptiveScaffold
import com.joetr.basil.navigation.toEditorJson
import com.joetr.basil.platform.ImageCapture
import com.joetr.basil.platform.ShareIntentHolder
import com.joetr.basil.platform.consumePlatformShareUrl
import com.joetr.basil.ui.motion.LocalRecipeAnimatedVisibilityScope
import com.joetr.basil.ui.motion.LocalSharedTransitionScope
import com.joetr.basil.ui.motion.recipeSharedElementTransitionSpec
import com.joetr.basil.ui.theme.BasilTheme
import kotlinx.coroutines.launch

private sealed interface RecipeShellState {
    data object Scaffold : RecipeShellState
    data class NarrowDetail(val id: String) : RecipeShellState
}

private fun recipeShellState(
    widthDp: Int,
    currentKey: Any?,
    topLevel: TopLevelDestination,
): RecipeShellState {
    if (
        widthDp < 960 &&
        topLevel == TopLevelDestination.Recipes &&
        currentKey is RecipeDetailKey
    ) {
        return RecipeShellState.NarrowDetail(currentKey.id)
    }
    return RecipeShellState.Scaffold
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSharedTransitionApi::class)
@Composable
public fun App(graph: AppGraph, initialWebPath: String? = null) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .basilImages(imageRepository = graph.imageRepositoryPublic)
            .build()
    }

    val themeMode by graph.settingsViewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    BasilTheme(darkTheme = darkTheme) {
        var topLevel by remember { mutableStateOf<TopLevelDestination>(TopLevelDestination.Recipes) }
        val backStack = remember {
            mutableStateListOf<Any>(
                initialWebPath?.let { com.joetr.basil.navigation.parseWebRoute(it) } ?: RecipesKey,
            )
        }
        var editorExtracted by remember { mutableStateOf<String?>(null) }
        var editorRecipeId by remember { mutableStateOf<String?>(null) }
        var cookRecipeId by remember { mutableStateOf<String?>(null) }
        var selectedRecipeId by remember { mutableStateOf<String?>(null) }
        var sharedImportUrl by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            consumePlatformShareUrl()?.let { ShareIntentHolder.pendingUrl = it }
            ShareIntentHolder.pendingUrl?.let { url ->
                ShareIntentHolder.pendingUrl = null
                topLevel = TopLevelDestination.Import
                backStack.clear()
                backStack += ImportKey
                sharedImportUrl = url
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthDp = maxWidth.value.toInt()
            val currentKey = backStack.lastOrNull()
            val canNavigateBack = when (currentKey) {
                is RecipeDetailKey, is ScanKey, is CookKey, is AuthKey, is EditorKey -> true
                else -> widthDp >= 960 && selectedRecipeId != null
            }
            BackHandler(enabled = canNavigateBack) {
                when (currentKey) {
                    is RecipeDetailKey, is ScanKey, is CookKey, is AuthKey, is EditorKey -> {
                        backStack.removeLast()
                    }
                    else -> selectedRecipeId = null
                }
            }
            when (currentKey) {
                is ScanKey -> ScanScreen(
                    viewModel = graph.scanViewModel,
                    onExtracted = { json -> backStack += EditorKey(extractedJson = json) },
                    onBack = { backStack.removeLast() },
                )
                is CookKey -> CookScreen(
                    viewModel = graph.cookViewModel,
                    recipeId = currentKey.recipeId,
                    onExit = { backStack.removeLast() },
                )
                is AuthKey -> AuthScreen(
                    viewModel = graph.authViewModel,
                    onDone = { backStack.removeLast() },
                )
                is EditorKey -> EditorScreen(
                    viewModel = graph.editorViewModel,
                    recipeId = currentKey.recipeId,
                    extractedJson = currentKey.extractedJson,
                    onSaved = {
                        topLevel = TopLevelDestination.Recipes
                        selectedRecipeId = null
                        backStack.clear()
                        backStack += RecipesKey
                    },
                    onBack = { backStack.removeLast() },
                )
                else -> {
                    val session by graph.settingsViewModel.session.collectAsState(initial = null)
                    val accountLabel = when (val s = session) {
                        is SessionState.Authenticated -> s.email ?: "Account"
                        is SessionState.Anonymous -> "Guest"
                        is SessionState.LocalPending -> "Local"
                        null -> null
                    }
                    val shellState = recipeShellState(widthDp, currentKey, topLevel)
                    SharedTransitionLayout(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            AnimatedContent(
                                targetState = shellState,
                                contentKey = { it },
                                transitionSpec = recipeSharedElementTransitionSpec(),
                                label = "recipe_shell",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) { state ->
                                CompositionLocalProvider(
                                    LocalRecipeAnimatedVisibilityScope provides this@AnimatedContent,
                                ) {
                                    when (state) {
                                        is RecipeShellState.NarrowDetail -> RecipeDetailScreen(
                                            viewModel = graph.recipeDetailViewModel,
                                            recipeId = state.id,
                                            onBack = { backStack.removeLast() },
                                            onEdit = { id -> backStack += EditorKey(recipeId = id) },
                                            onCook = { id -> backStack += CookKey(recipeId = id) },
                                            onDeleted = { backStack.removeLast() },
                                        )
                                        RecipeShellState.Scaffold -> AdaptiveScaffold(
                                            widthDp = widthDp,
                                            selected = topLevel,
                                            onNavigate = { dest ->
                                                topLevel = dest
                                                selectedRecipeId = null
                                                backStack.clear()
                                                backStack += when (dest) {
                                                    TopLevelDestination.Recipes -> RecipesKey
                                                    TopLevelDestination.Import -> ImportKey
                                                    TopLevelDestination.Account -> AccountKey
                                                }
                                            },
                                            accountLabel = accountLabel,
                                        ) {
                                            when (topLevel) {
                                                TopLevelDestination.Recipes -> {
                                                    val openDetail: (String) -> Unit = { id ->
                                                        if (widthDp >= 960) selectedRecipeId = id
                                                        else backStack += RecipeDetailKey(id)
                                                    }
                                                    RecipesTabContent(
                                                        widthDp = widthDp,
                                                        selectedRecipeId = selectedRecipeId,
                                                        recipesViewModel = graph.recipesViewModel,
                                                        detailViewModel = graph.recipeDetailViewModel,
                                                        onRecipeClick = openDetail,
                                                        onCloseDetail = { selectedRecipeId = null },
                                                        onAddRecipe = { backStack += EditorKey() },
                                                        onEdit = { recipeId -> backStack += EditorKey(recipeId = recipeId) },
                                                        onCook = { recipeId -> backStack += CookKey(recipeId = recipeId) },
                                                        onDeleted = { selectedRecipeId = null },
                                                    )
                                                }
                                                TopLevelDestination.Import -> ImportScreen(
                                                    viewModel = graph.importViewModel,
                                                    onExtracted = { json -> backStack += EditorKey(extractedJson = json) },
                                                    onScan = if (ImageCapture.isAvailable) ({ backStack += ScanKey }) else null,
                                                    initialUrl = sharedImportUrl,
                                                )
                                                TopLevelDestination.Account -> AccountScreen(
                                                    viewModel = graph.settingsViewModel,
                                                    onSignIn = { backStack += AuthKey },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
