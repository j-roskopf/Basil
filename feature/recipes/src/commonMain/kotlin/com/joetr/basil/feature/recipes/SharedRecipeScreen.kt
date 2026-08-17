package com.joetr.basil.feature.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.joetr.basil.domain.model.SharedRecipe
import com.joetr.basil.domain.share.RecipeShareTextFormatter
import com.joetr.basil.domain.usecase.GetSharedRecipeUseCase
import com.joetr.basil.domain.usecase.SaveSharedRecipeCopyUseCase
import com.joetr.basil.platform.shareText
import com.joetr.basil.ui.components.BasilCard
import com.joetr.basil.ui.components.DialogActionButton
import com.joetr.basil.ui.components.RecipeImage
import com.joetr.basil.ui.components.SectionHeader
import com.joetr.basil.ui.icons.BasilAppMark
import com.joetr.basil.ui.icons.BasilIcons
import com.joetr.basil.ui.icons.BasilIcon
import com.joetr.basil.ui.layout.basilSafeArea
import com.joetr.basil.ui.theme.BasilRadii
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public sealed interface SharedRecipeState {
    public data object Loading : SharedRecipeState
    public data class Loaded(val recipe: SharedRecipe) : SharedRecipeState
    public data class Error(val message: String) : SharedRecipeState
}

public class SharedRecipeViewModel(
    private val getSharedRecipe: GetSharedRecipeUseCase,
    private val saveSharedRecipeCopy: SaveSharedRecipeCopyUseCase,
) {
    private val _state = MutableStateFlow<SharedRecipeState>(SharedRecipeState.Loading)
    public val state: StateFlow<SharedRecipeState> = _state.asStateFlow()

    public suspend fun load(token: String) {
        _state.value = SharedRecipeState.Loading
        _state.value = runCatching { getSharedRecipe(token) }
            .fold(
                onSuccess = { SharedRecipeState.Loaded(it) },
                onFailure = { SharedRecipeState.Error(it.message ?: "This share link is no longer available.") },
            )
    }

    public suspend fun saveCopy(recipe: SharedRecipe) = saveSharedRecipeCopy(recipe)
}

@Composable
public fun SharedRecipeScreen(
    viewModel: SharedRecipeViewModel,
    token: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var saved by remember(token) { mutableStateOf(false) }
    var saveError by remember(token) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        viewModel.load(token)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .basilSafeArea(),
    ) {
        when (val current = state) {
            SharedRecipeState.Loading -> Text(
                "Loading recipe…",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is SharedRecipeState.Error -> Column(
                modifier = Modifier.align(Alignment.Center).padding(BasilSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "This recipe link is unavailable",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(BasilSpacing.sm))
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(BasilSpacing.lg))
                DialogActionButton(text = "Back", onClick = onBack)
            }
            is SharedRecipeState.Loaded -> SharedRecipeContent(
                recipe = current.recipe,
                saved = saved,
                saveError = saveError,
                onBack = onBack,
                onShare = { shareText(RecipeShareTextFormatter.format(current.recipe.toRecipe(), current.recipe.url)) },
                onSave = {
                    saveError = null
                    scope.launch {
                        runCatching { viewModel.saveCopy(current.recipe) }
                            .onSuccess { saved = true }
                            .onFailure { saveError = it.message ?: "Could not save this recipe." }
                    }
                },
            )
        }
    }
}

@Composable
private fun SharedRecipeContent(
    recipe: SharedRecipe,
    saved: Boolean,
    saveError: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BasilSpacing.xxl, vertical = BasilSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BasilSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm)) {
                BasilAppMark(tint = MaterialTheme.colorScheme.primary, size = 28.dp)
                Text("Basil", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            DialogActionButton(text = "Back", onClick = onBack)
        }

        BasilCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(BasilSpacing.lg)) {
                RecipeImage(
                    title = recipe.title,
                    imageUrl = recipe.imageUrl,
                    imageModel = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(BasilRadii.image)),
                    shape = RoundedCornerShape(BasilRadii.image),
                )
                Spacer(Modifier.height(BasilSpacing.lg))
                Text(recipe.title, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                recipe.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(BasilSpacing.sm))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(BasilSpacing.md))
                SharedRecipeMeta(recipe)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm)) {
            DialogActionButton(
                text = if (saved) "Saved to your recipes" else "Save a copy",
                onClick = onSave,
                color = if (saved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            DialogActionButton(text = "Share", onClick = onShare, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saveError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        if (recipe.ingredients.isNotEmpty()) {
            BasilCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(BasilSpacing.lg)) {
                    SectionHeader("Ingredients")
                    Spacer(Modifier.height(BasilSpacing.sm))
                    recipe.ingredients.forEach { ingredient ->
                        Text(ingredient, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(BasilSpacing.sm))
                    }
                }
            }
        }
        if (recipe.steps.isNotEmpty()) {
            BasilCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(BasilSpacing.lg)) {
                    SectionHeader("Steps")
                    Spacer(Modifier.height(BasilSpacing.sm))
                    recipe.steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. ${step.text}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(BasilSpacing.md))
                    }
                }
            }
        }
        recipe.sourceUrl?.let {
            Text("Source: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SharedRecipeMeta(recipe: SharedRecipe) {
    Row(horizontalArrangement = Arrangement.spacedBy(BasilSpacing.lg)) {
        recipe.servings?.let { SharedRecipeMetaItem(BasilIcons.Account, "$it servings") }
        val minutes = listOfNotNull(recipe.prepMinutes, recipe.cookMinutes).sum().takeIf { it > 0 }
        minutes?.let { SharedRecipeMetaItem(BasilIcons.Clock, "$it min") }
    }
}

@Composable
private fun SharedRecipeMetaItem(
    icon: com.joetr.basil.ui.icons.BasilIconPainter,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BasilSpacing.xs)) {
        BasilIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun SharedRecipe.toRecipe(): com.joetr.basil.domain.model.Recipe =
    com.joetr.basil.domain.model.Recipe(
        id = token,
        ownerId = "shared",
        title = title,
        description = description,
        imageUrl = imageUrl,
        sourceUrl = sourceUrl,
        servings = servings,
        prepMinutes = prepMinutes,
        cookMinutes = cookMinutes,
        ingredients = ingredients,
        steps = steps,
        tags = tags,
        createdAt = 0L,
        updatedAt = 0L,
    )
