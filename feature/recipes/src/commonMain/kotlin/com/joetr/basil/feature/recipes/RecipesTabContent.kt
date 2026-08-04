package com.joetr.basil.feature.recipes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.joetr.basil.ui.motion.LocalRecipeAnimatedVisibilityScope
import com.joetr.basil.ui.motion.recipeSharedElementTransitionSpec

private const val ListPaneFraction = 0.42f
private const val DetailPaneFraction = 0.58f

private val paneSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun RecipesTabContent(
    widthDp: Int,
    selectedRecipeId: String?,
    recipesViewModel: RecipesViewModel,
    detailViewModel: RecipeDetailViewModel,
    onRecipeClick: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onAddRecipe: () -> Unit,
    onEdit: (String) -> Unit,
    onCook: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    if (widthDp >= 960) {
        val detailOpen = selectedRecipeId != null
        val paneTransition = updateTransition(detailOpen, label = "recipe_panes")

        AnimatedContent(
            targetState = selectedRecipeId,
            contentKey = { it != null },
            transitionSpec = recipeSharedElementTransitionSpec(),
            label = "recipe_wide_master_detail",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { detailId ->
            CompositionLocalProvider(
                LocalRecipeAnimatedVisibilityScope provides this@AnimatedContent,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val totalWidth = maxWidth
                    val listFraction by paneTransition.animateFloat(
                        transitionSpec = { paneSpring },
                        label = "list_fraction",
                    ) { open -> if (open) ListPaneFraction else 1f }
                    val detailFraction by paneTransition.animateFloat(
                        transitionSpec = { paneSpring },
                        label = "detail_fraction",
                    ) { open -> if (open) DetailPaneFraction else 0f }
                    val detailSlideFraction by paneTransition.animateFloat(
                        transitionSpec = { paneSpring },
                        label = "detail_slide",
                    ) { open -> if (open) 0f else 1f }

                    Row(Modifier.fillMaxSize().clipToBounds()) {
                        Box(
                            Modifier
                                .width(totalWidth * listFraction)
                                .fillMaxHeight(),
                        ) {
                            RecipesScreen(
                                viewModel = recipesViewModel,
                                onRecipeClick = onRecipeClick,
                                onAddRecipe = onAddRecipe,
                                detailOpenRecipeId = detailId,
                            )
                        }
                        if (detailId != null) {
                            Box(
                                Modifier
                                    .width(totalWidth * detailFraction.coerceAtLeast(0.001f))
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        translationX = size.width * detailSlideFraction
                                    }
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                RecipeDetailScreen(
                                    viewModel = detailViewModel,
                                    recipeId = detailId,
                                    onBack = onCloseDetail,
                                    onEdit = onEdit,
                                    onCook = onCook,
                                    onDeleted = onDeleted,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        RecipesScreen(
            viewModel = recipesViewModel,
            onRecipeClick = onRecipeClick,
            onAddRecipe = onAddRecipe,
        )
    }
}
