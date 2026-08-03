package com.joetr.basil.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode

public val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

public val LocalRecipeAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Fade keeps outgoing content alive for shared-element matching; opaque roots prevent white flash. */
public fun recipeSharedElementTransitionSpec(): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
    (fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)))
        .using(SizeTransform(clip = false))
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun Modifier.sharedRecipeImage(recipeId: String): Modifier {
    if (LocalInspectionMode.current) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalRecipeAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedRecipeImage.sharedElement(
            sharedContentState = rememberSharedContentState(key = "recipe_image_$recipeId"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun Modifier.sharedRecipeTitle(recipeId: String): Modifier {
    if (LocalInspectionMode.current) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalRecipeAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedRecipeTitle.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "recipe_title_$recipeId"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) },
        )
    }
}
