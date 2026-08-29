package com.joetr.basil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.joetr.basil.platform.imageUrlForDisplay

private const val MinScale = 1f
private const val MaxScale = 5f
private const val DoubleTapScale = 2.5f

@Composable
public fun RecipeImageFullscreen(
    title: String,
    imageModel: Any?,
    imageUrl: String?,
    onDismiss: () -> Unit,
) {
    val model = imageModel ?: imageUrlForDisplay(imageUrl) ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(MinScale) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(Size.Zero) }

        fun clampOffset(raw: Offset, currentScale: Float): Offset {
            if (currentScale <= MinScale || containerSize == Size.Zero) return Offset.Zero
            val maxX = containerSize.width * (currentScale - 1f) / 2f
            val maxY = containerSize.height * (currentScale - 1f) / 2f
            return Offset(
                x = raw.x.coerceIn(-maxX, maxX),
                y = raw.y.coerceIn(-maxY, maxY),
            )
        }

        fun resetTransform() {
            scale = MinScale
            offset = Offset.Zero
        }

        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            val newScale = (scale * zoomChange).coerceIn(MinScale, MaxScale)
            scale = newScale
            offset = clampOffset(offset + panChange, newScale)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it.toSize() }
                .pointerInput(scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > MinScale) {
                                resetTransform()
                            } else {
                                scale = DoubleTapScale
                            }
                        },
                        onTap = {
                            if (scale <= MinScale) onDismiss()
                        },
                    )
                }
                .transformable(
                    state = transformableState,
                    canPan = { scale > MinScale },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
