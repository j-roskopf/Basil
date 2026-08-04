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
import com.joetr.basil.ui.icons.BasilIcon
import com.joetr.basil.ui.icons.BasilIcons
import com.joetr.basil.ui.layout.basilSafeArea
import com.joetr.basil.ui.theme.BasilColors
import com.joetr.basil.ui.theme.BasilRadii
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.delay
import kotlin.math.abs

public class CookViewModel(
    private val observeRecipe: ObserveRecipeUseCase,
) {
    public fun recipe(id: String) = observeRecipe(id)
}

/**
 * A deliberately sparse, paged cooking surface. Page zero is ingredients; the following
 * pages center the current instruction while keeping neighbouring instructions subdued.
 */
@Composable
public fun CookScreen(
    viewModel: CookViewModel,
    recipeId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe by viewModel.recipe(recipeId).collectAsState(initial = null)
    val steps = recipe?.steps.orEmpty()
    var page by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var timerSeconds by remember { mutableLongStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    keepScreenOn(true)

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
        page = target.coerceIn(0, steps.size)
        timerSeconds = 0L
        timerRunning = false
        showTimer = false
    }

    val stepIndex = page - 1
    val currentMinutes = steps.getOrNull(stepIndex)?.minutes
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.27f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BasilColors.CookBackground)
            .basilSafeArea()
            .pointerInput(page, steps.size) {
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
        if (page < steps.size) {
            Text(
                "›",
                color = muted,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 330.dp)
                .padding(horizontal = 30.dp)
                .verticalScroll(rememberScrollState())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (page < steps.size) goTo(page + 1) },
            verticalArrangement = Arrangement.Center,
        ) {
            if (page == 0) {
                if (recipe?.ingredients.isNullOrEmpty()) {
                    Text("No ingredients for this recipe.", style = MaterialTheme.typography.bodyLarge, color = muted)
                } else {
                    recipe?.ingredients?.forEach { ingredient ->
                        IngredientLine(ingredient, Modifier.padding(vertical = 5.dp))
                    }
                }
            } else if (steps.isEmpty()) {
                Text("No steps for this recipe.", style = MaterialTheme.typography.bodyLarge, color = muted)
            } else {
                steps.getOrNull(stepIndex - 1)?.let { previous ->
                    FocusStepText(stepIndex, previous.text, muted)
                    Spacer(Modifier.padding(top = 13.dp))
                }
                FocusStepText(stepIndex + 1, steps[stepIndex].text, MaterialTheme.colorScheme.onSurface)
                steps.getOrNull(stepIndex + 1)?.let { next ->
                    Spacer(Modifier.padding(top = 13.dp))
                    FocusStepText(stepIndex + 2, next.text, muted)
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
        if (page < steps.size) {
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
            if (currentMinutes != null) {
                CircleIconButton(
                    icon = BasilIcons.Timer,
                    contentDescription = "Timer",
                    onClick = {
                        showTimer = !showTimer
                        if (showTimer && timerSeconds == 0L) timerSeconds = currentMinutes * 60L
                    },
                    tint = if (showTimer || timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
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
                Text(formatTimer(timerSeconds), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
            Text(recipe?.title ?: "Recipe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun FocusStepText(number: Int, text: String, color: Color) {
    Text(
        text = "$number  $text",
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
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
