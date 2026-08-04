package com.joetr.basil.feature.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.joetr.basil.ui.components.BasilAlertDialog
import com.joetr.basil.ui.components.BasilConfirmDialog
import com.joetr.basil.ui.components.DialogActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.recipe.IngredientScaler
import com.joetr.basil.domain.model.RecipeStep
import com.joetr.basil.domain.usecase.DeleteRecipeUseCase
import com.joetr.basil.domain.usecase.ObserveRecipeUseCase
import com.joetr.basil.domain.usecase.ToggleFavouriteUseCase
import com.joetr.basil.ui.components.CircleIconButton
import com.joetr.basil.ui.components.DetailAction
import com.joetr.basil.ui.components.DetailStat
import com.joetr.basil.ui.components.HairlineDivider
import com.joetr.basil.ui.components.IngredientLine
import com.joetr.basil.ui.components.RecipeImage
import com.joetr.basil.ui.components.RecipeImageFullscreen
import com.joetr.basil.ui.components.SectionHeader
import com.joetr.basil.ui.components.hostFromUrl
import basil.ui.generated.resources.BasilAssetIcons
import com.joetr.basil.ui.icons.BasilIcons
import com.joetr.basil.ui.layout.basilSafeArea
import com.joetr.basil.ui.motion.sharedRecipeImage
import com.joetr.basil.ui.motion.sharedRecipeTitle
import com.joetr.basil.ui.theme.BasilRadii
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private val RecipeDetailCompactBreakpoint = 1000.dp

public class RecipeDetailViewModel(
    private val observeRecipe: ObserveRecipeUseCase,
    private val deleteRecipe: DeleteRecipeUseCase,
    private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
) {
    public fun recipe(id: String): Flow<Recipe> =
        observeRecipe(id).filterNotNull()

    public suspend fun delete(id: String) = deleteRecipe(id)

    public suspend fun toggleFavourite(id: String) = toggleFavouriteUseCase(id)
}

@Composable
public fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    recipeId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCook: (String) -> Unit,
    onDeleted: () -> Unit = onBack,
    modifier: Modifier = Modifier,
) {
    val recipe by viewModel.recipe(recipeId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }
    var showAdjustServings by remember { mutableStateOf(false) }
    var adjustedServings by remember(recipeId) { mutableStateOf<Int?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        recipe?.let { item ->
            val imageModel = item.localImageId?.let { "local-image://$it" }
            val hasImage = imageModel != null || !item.imageUrl.isNullOrBlank()
            val totalMinutes = listOfNotNull(item.prepMinutes, item.cookMinutes).sum().takeIf { it > 0 }
                ?: item.steps.mapNotNull { it.minutes }.sum().takeIf { it > 0 }

            if (showFullscreenImage && hasImage) {
                RecipeImageFullscreen(
                    title = item.title,
                    imageModel = imageModel,
                    imageUrl = item.imageUrl,
                    onDismiss = { showFullscreenImage = false },
                )
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .basilSafeArea(),
            ) {
                val compactLayout = maxWidth < RecipeDetailCompactBreakpoint
                if (compactLayout) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        RecipeDetailHeroSection(
                            recipeId = recipeId,
                            item = item,
                            imageModel = imageModel,
                            hasImage = hasImage,
                            onBack = onBack,
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            onFullscreenImage = { showFullscreenImage = true },
                            onToggleFavourite = { scope.launch { viewModel.toggleFavourite(item.id) } },
                            onEdit = { onEdit(item.id) },
                            onDelete = { showDeleteConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
                        RecipeDetailContent(
                            recipeId = recipeId,
                            item = item,
                            totalMinutes = totalMinutes,
                            adjustedServings = adjustedServings,
                            onCook = { onCook(item.id) },
                            onEdit = { onEdit(item.id) },
                            onAdjustServings = { showAdjustServings = true },
                            twoColumnRecipeBody = false,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        RecipeDetailHeroSection(
                            recipeId = recipeId,
                            item = item,
                            imageModel = imageModel,
                            hasImage = hasImage,
                            onBack = onBack,
                            showMenu = showMenu,
                            onShowMenuChange = { showMenu = it },
                            onFullscreenImage = { showFullscreenImage = true },
                            onToggleFavourite = { scope.launch { viewModel.toggleFavourite(item.id) } },
                            onEdit = { onEdit(item.id) },
                            onDelete = { showDeleteConfirm = true },
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight(),
                        )
                        Column(
                            Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = BasilSpacing.xxxl, vertical = BasilSpacing.xxl),
                        ) {
                            RecipeDetailContent(
                                recipeId = recipeId,
                                item = item,
                                totalMinutes = totalMinutes,
                                adjustedServings = adjustedServings,
                                onCook = { onCook(item.id) },
                                onEdit = { onEdit(item.id) },
                                onAdjustServings = { showAdjustServings = true },
                                twoColumnRecipeBody = true,
                            )
                        }
                    }
                }
            }

            if (showAdjustServings) {
                val baseServings = item.servings ?: 1
                ServingsAdjustDialog(
                    baseServings = baseServings,
                    hasOriginalServings = item.servings != null,
                    currentTarget = adjustedServings,
                    onDismiss = { showAdjustServings = false },
                    onApply = { target ->
                        adjustedServings = if (target == baseServings) null else target
                        showAdjustServings = false
                    },
                    onReset = {
                        adjustedServings = null
                        showAdjustServings = false
                    },
                )
            }

            if (showDeleteConfirm) {
                BasilConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = "Delete recipe?",
                    message = "This will remove the recipe from your library on all synced devices.",
                    confirmText = "Delete",
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.delete(item.id)
                            onDeleted()
                        }
                    },
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailHeroSection(
    recipeId: String,
    item: Recipe,
    imageModel: String?,
    hasImage: Boolean,
    onBack: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onFullscreenImage: () -> Unit,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.then(
            if (hasImage) Modifier.clickable(onClick = onFullscreenImage) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        RecipeImage(
            title = item.title,
            imageUrl = item.imageUrl,
            imageModel = imageModel,
            modifier = Modifier
                .sharedRecipeImage(recipeId)
                .fillMaxSize(),
            shape = RoundedCornerShape(BasilRadii.image),
        )
        CircleIconButton(
            icon = BasilIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            onImage = true,
            modifier = Modifier.align(Alignment.TopStart).padding(start = BasilSpacing.gutter, top = 18.dp),
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end = BasilSpacing.gutter, top = 18.dp)) {
            CircleIconButton(
                icon = BasilIcons.More,
                contentDescription = "More",
                onClick = { onShowMenuChange(true) },
                onImage = true,
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenuChange(false) },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(if (item.isFavourite) "Remove from favourites" else "Add to favourites")
                    },
                    onClick = {
                        onShowMenuChange(false)
                        onToggleFavourite()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit recipe") },
                    onClick = {
                        onShowMenuChange(false)
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete recipe", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onShowMenuChange(false)
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailContent(
    recipeId: String,
    item: Recipe,
    totalMinutes: Int?,
    adjustedServings: Int?,
    onCook: () -> Unit,
    onEdit: () -> Unit,
    onAdjustServings: () -> Unit,
    twoColumnRecipeBody: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseServings = item.servings ?: 1
    val displayServings = adjustedServings ?: item.servings
    val displayIngredients = if (adjustedServings != null) {
        IngredientScaler.scaleIngredients(item.ingredients, baseServings, adjustedServings)
    } else {
        item.ingredients
    }
    Column(
        modifier.then(
            if (twoColumnRecipeBody) Modifier else Modifier.padding(horizontal = BasilSpacing.xxl),
        ),
    ) {
        if (!twoColumnRecipeBody) {
            Spacer(Modifier.height(BasilSpacing.xl))
        }
        Text(
            item.title,
            modifier = Modifier.sharedRecipeTitle(recipeId),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BasilSpacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm),
        ) {
            hostFromUrl(item.sourceUrl)?.let { host ->
                Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            displayServings?.let { servings ->
                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                val servingsLabel = if (adjustedServings != null) "$servings servings (adjusted)" else "$servings servings"
                Text(servingsLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(BasilSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            displayServings?.let {
                DetailStat(icon = BasilIcons.Account, value = "$it")
            }
            item.prepMinutes?.let {
                DetailStat(icon = BasilIcons.Clock, value = "${it}min", label = "Prep")
            }
            totalMinutes?.let {
                if (item.prepMinutes != null) {
                    Text("|", color = MaterialTheme.colorScheme.outline)
                }
                DetailStat(icon = BasilIcons.Clock, value = "${it}min", label = "Total")
            }
        }

        Spacer(Modifier.height(BasilSpacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.xl),
        ) {
            DetailAction(
                icon = BasilIcons.Play,
                label = "Cook",
                onClick = onCook,
            )
            if (item.ingredients.isNotEmpty()) {
                DetailAction(
                    assetIcon = BasilAssetIcons.Adjust,
                    label = "Adjust",
                    onClick = onAdjustServings,
                    iconSize = 24.dp,
                )
            }
            DetailAction(
                assetIcon = BasilAssetIcons.Edit,
                label = "Edit",
                onClick = onEdit,
                iconSize = 22.dp,
            )
        }

        Spacer(Modifier.height(BasilSpacing.lg))
        HairlineDivider()

        item.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(BasilSpacing.xl))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (item.ingredients.isNotEmpty() || item.steps.isNotEmpty()) {
            Spacer(Modifier.height(BasilSpacing.lg))
            HairlineDivider()
            Spacer(Modifier.height(BasilSpacing.lg))
        }

        if (twoColumnRecipeBody && displayIngredients.isNotEmpty() && item.steps.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BasilSpacing.xxxl),
                verticalAlignment = Alignment.Top,
            ) {
                RecipeDetailIngredientsSection(
                    ingredients = displayIngredients,
                    modifier = Modifier.weight(1f),
                )
                RecipeDetailStepsSection(
                    steps = item.steps,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            if (displayIngredients.isNotEmpty()) {
                RecipeDetailIngredientsSection(ingredients = displayIngredients)
            }
            if (item.steps.isNotEmpty()) {
                if (displayIngredients.isNotEmpty()) {
                    Spacer(Modifier.height(BasilSpacing.xl))
                }
                RecipeDetailStepsSection(steps = item.steps)
            }
        }

        Spacer(Modifier.height(BasilSpacing.xxxl))
    }
}

@Composable
private fun ServingsAdjustDialog(
    baseServings: Int,
    hasOriginalServings: Boolean,
    currentTarget: Int?,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var input by remember(currentTarget, baseServings) {
        mutableStateOf((currentTarget ?: baseServings).toString())
    }
    val target = input.toIntOrNull()
    val canApply = target != null && target > 0

    BasilAlertDialog(
        onDismissRequest = onDismiss,
        title = "Adjust servings",
        dismissButton = {
            if (currentTarget != null) {
                DialogActionButton(
                    text = "Reset",
                    onClick = onReset,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DialogActionButton(text = "Cancel", onClick = onDismiss)
        },
        confirmButton = {
            DialogActionButton(
                text = "Apply",
                onClick = { target?.takeIf { it > 0 }?.let(onApply) },
                color = if (canApply) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(
            if (hasOriginalServings) {
                "This recipe serves $baseServings. How many do you want to make?"
            } else {
                "Enter how many servings you want to make. We'll scale ingredient amounts from a single serving."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(BasilSpacing.lg))
        BasicTextField(
            value = input,
            onValueChange = { input = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BasilRadii.field))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = BasilSpacing.md, vertical = BasilSpacing.md),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (input.isBlank()) {
                    Text(
                        "Servings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun RecipeDetailIngredientsSection(
    ingredients: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader("Ingredients")
        Spacer(Modifier.height(BasilSpacing.sm))
        ingredients.forEach { ingredient ->
            IngredientLine(ingredient)
        }
    }
}

@Composable
private fun RecipeDetailStepsSection(
    steps: List<RecipeStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader("Steps")
        Spacer(Modifier.height(BasilSpacing.sm))
        steps.forEachIndexed { index, step ->
            Text(
                "${index + 1}. ${step.text}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = BasilSpacing.sm),
            )
        }
    }
}
