package com.joetr.basil.feature.cook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joetr.basil.domain.usecase.ObserveRecipeUseCase
import com.joetr.basil.platform.hapticSuccess
import com.joetr.basil.platform.keepScreenOn
import com.joetr.basil.platform.playTimerCompleteSound
import com.joetr.basil.ui.components.CircleIconButton
import com.joetr.basil.ui.components.IngredientLine
import com.joetr.basil.ui.components.RecipeImage
import com.joetr.basil.ui.components.RecipeImageFullscreen
import com.joetr.basil.ui.icons.BasilIcons
import com.joetr.basil.ui.layout.basilSafeArea
import com.joetr.basil.ui.theme.BasilColors
import com.joetr.basil.ui.theme.BasilRadii
import com.joetr.basil.ui.theme.BasilSpacing
import com.joetr.basil.ui.theme.BasilTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

public class CookViewModel(
    private val observeRecipe: ObserveRecipeUseCase,
) {
    public fun recipe(id: String) = observeRecipe(id)
}

private sealed interface CookPage {
    data object Ingredients : CookPage
    data object Image : CookPage
    data class Step(val index: Int) : CookPage
}

/**
 * A deliberately sparse, paged cooking surface. Cook mode always uses a dark canvas so
 * light-mode app theme cannot wash out step and ingredient text. Pages are ingredients,
 * then steps, then an optional recipe image (image-only recipes open on the image so the
 * display can stay awake while cooking from a photo).
 */
@Composable
public fun CookScreen(
    viewModel: CookViewModel,
    recipeId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cook canvas is always dark; nest a dark scheme so Material tokens stay legible.
    BasilTheme(darkTheme = true) {
        CookScreenContent(
            viewModel = viewModel,
            recipeId = recipeId,
            onExit = onExit,
            modifier = modifier,
        )
    }
}

@Composable
private fun CookScreenContent(
    viewModel: CookViewModel,
    recipeId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe by viewModel.recipe(recipeId).collectAsState(initial = null)
    val steps = recipe?.steps.orEmpty()
    val ingredients = recipe?.ingredients.orEmpty()
    val imageModel = recipe?.localImageId?.let { "local-image://$it" }
    val imageUrl = recipe?.imageUrl
    val hasImage = imageModel != null || !imageUrl.isNullOrBlank()

    val pages = remember(ingredients.size, steps.size, hasImage) {
        buildCookPages(
            hasIngredients = ingredients.isNotEmpty(),
            stepCount = steps.size,
            hasImage = hasImage,
        )
    }

    var page by remember(recipeId) { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var timerSeconds by remember { mutableLongStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        keepScreenOn(true)
        onDispose { keepScreenOn(false) }
    }

    LaunchedEffect(timerRunning, timerSeconds) {
        if (!timerRunning || timerSeconds <= 0L) return@LaunchedEffect
        delay(1_000)
        timerSeconds -= 1
        if (timerSeconds == 0L) {
            timerRunning = false
            hapticSuccess()
            playTimerCompleteSound()
        }
    }

    fun goTo(target: Int) {
        page = target.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        timerSeconds = 0L
        timerRunning = false
        showTimer = false
    }

    val currentPage = pages.getOrNull(page)
    val stepIndex = (currentPage as? CookPage.Step)?.index
    val currentMinutes = stepIndex?.let { steps.getOrNull(it)?.minutes }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.27f)
    val lastPage = pages.lastIndex.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BasilColors.CookBackground)
            .basilSafeArea()
            .pointerInput(page, pages.size) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragAccum += amount },
                    onDragEnd = {
                        when {
                            dragAccum < -48f -> goTo(page + 1)
                            dragAccum > 48f -> goTo(page - 1)
                        }
                        dragAccum = 0f
                    },
                )
            },
    ) {
        if (page > 0) {
            Text(
                "‹",
                color = muted,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
            )
        }
        if (page < lastPage) {
            Text(
                "›",
                color = muted,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            )
        }

        when (currentPage) {
            CookPage.Image -> {
                CookImagePage(
                    title = recipe?.title ?: "Recipe",
                    imageModel = imageModel,
                    imageUrl = imageUrl,
                    onOpenFullscreen = { showFullscreenImage = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp, vertical = 72.dp),
                )
            }
            CookPage.Ingredients, is CookPage.Step, null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 330.dp)
                        .padding(horizontal = 30.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (page < lastPage) goTo(page + 1) },
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (currentPage) {
                        CookPage.Ingredients, null -> {
                            if (ingredients.isEmpty()) {
                                Text(
                                    "No ingredients for this recipe.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = muted,
                                )
                            } else {
                                ingredients.forEach { ingredient ->
                                    IngredientLine(ingredient, Modifier.padding(vertical = 5.dp))
                                }
                            }
                        }
                        is CookPage.Step -> {
                            val index = currentPage.index
                            steps.getOrNull(index - 1)?.let { previous ->
                                FocusStepText(index, previous.text, muted)
                                Spacer(Modifier.padding(top = 13.dp))
                            }
                            FocusStepText(
                                index + 1,
                                steps[index].text,
                                MaterialTheme.colorScheme.onSurface,
                            )
                            steps.getOrNull(index + 1)?.let { next ->
                                Spacer(Modifier.padding(top = 13.dp))
                                FocusStepText(index + 2, next.text, muted)
                            }
                        }
                        CookPage.Image -> Unit
                    }
                }
            }
        }

        if (page > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.25f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { goTo(page - 1) },
            )
        }
        if (page < lastPage) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.25f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { goTo(page + 1) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconButton(BasilIcons.Close, "Close cook mode", onExit)
            Row(horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm)) {
                if (hasImage && currentPage != CookPage.Image) {
                    CircleIconButton(
                        icon = BasilIcons.Photo,
                        contentDescription = "View recipe image",
                        onClick = {
                            val imagePage = pages.indexOf(CookPage.Image)
                            if (imagePage >= 0) goTo(imagePage) else showFullscreenImage = true
                        },
                    )
                }
                if (currentMinutes != null) {
                    CircleIconButton(
                        icon = BasilIcons.Timer,
                        contentDescription = "Timer",
                        onClick = {
                            showTimer = !showTimer
                            if (showTimer && timerSeconds == 0L) timerSeconds = currentMinutes * 60L
                        },
                        tint = if (showTimer || timerRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        if (showTimer && currentMinutes != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 62.dp)
                    .clip(RoundedCornerShape(BasilRadii.field))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { timerRunning = !timerRunning }
                    .padding(horizontal = BasilSpacing.lg, vertical = BasilSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatTimer(timerSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (timerRunning) "Tap to pause" else "Tap to start",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(BasilRadii.field))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                recipe?.title ?: "Recipe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }

        if (showFullscreenImage && hasImage) {
            RecipeImageFullscreen(
                title = recipe?.title ?: "Recipe",
                imageModel = imageModel,
                imageUrl = imageUrl,
                onDismiss = { showFullscreenImage = false },
            )
        }
    }
}

@Composable
private fun CookImagePage(
    title: String,
    imageModel: Any?,
    imageUrl: String?,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenFullscreen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageModel == null && imageUrl.isNullOrBlank()) {
            Text(
                "No image for this recipe.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.27f),
            )
        } else {
            RecipeImage(
                title = title,
                imageUrl = imageUrl,
                imageModel = imageModel,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(BasilRadii.image),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun buildCookPages(
    hasIngredients: Boolean,
    stepCount: Int,
    hasImage: Boolean,
): List<CookPage> {
    val imageOnly = hasImage && !hasIngredients && stepCount == 0
    if (imageOnly) return listOf(CookPage.Image)
    return buildList {
        add(CookPage.Ingredients)
        repeat(stepCount) { add(CookPage.Step(it)) }
        if (hasImage) add(CookPage.Image)
    }
}

@Composable
private fun FocusStepText(number: Int, text: String, color: Color) {
    Text(
        text = "$number  $text",
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 17.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = color,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatTimer(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = abs(totalSeconds % 60)
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
