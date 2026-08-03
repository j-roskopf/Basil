package com.joetr.basil.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.joetr.basil.platform.pickDesktopOpenFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberRecipeImagePicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): RecipeImagePickerControls {
    val scope = rememberCoroutineScope()
    return remember {
        RecipeImagePickerControls(
            pickFromGallery = {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            pickDesktopOpenFile(
                                title = "Choose recipe image",
                                extensions = setOf("jpg", "jpeg", "png", "webp", "heic"),
                            )
                        }.getOrElse {
                            onError(it.message ?: "Could not read image")
                            null
                        }
                    }
                    if (bytes != null) onImagePicked(bytes)
                }
            },
            takePhoto = { onError("Camera is not available on desktop") },
            canPickGallery = true,
            canTakePhoto = false,
        )
    }
}
