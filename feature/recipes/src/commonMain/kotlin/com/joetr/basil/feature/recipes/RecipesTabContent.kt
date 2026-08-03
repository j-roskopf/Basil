package com.joetr.basil.feature.recipes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.joetr.basil.ui.motion.LocalRecipeAnimatedVisibilityScope
import com.joetr.basil.ui.motion.recipeSharedElementTransitionSpec

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
        AnimatedContent(
            targetState = selectedRecipeId,
            contentKey = { it },
            transitionSpec = recipeSharedElementTransitionSpec(),
            label = "recipe_wide_master_detail",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { detailId ->
            CompositionLocalProvider(
                LocalRecipeAnimatedVisibilityScope provides this@AnimatedContent,
            ) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(if (detailId != null) 0.42f else 1f)) {
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
                                .weight(0.58f)
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
    } else {
        RecipesScreen(
            viewModel = recipesViewModel,
            onRecipeClick = onRecipeClick,
            onAddRecipe = onAddRecipe,
        )
    }
}
