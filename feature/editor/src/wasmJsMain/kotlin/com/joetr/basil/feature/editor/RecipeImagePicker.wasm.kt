package com.joetr.basil.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@Composable
internal actual fun rememberRecipeImagePicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): RecipeImagePickerControls {
    return remember {
        RecipeImagePickerControls(
            pickFromGallery = { openFileInput(capture = false, onImagePicked = onImagePicked, onError = onError) },
            takePhoto = { openFileInput(capture = true, onImagePicked = onImagePicked, onError = onError) },
            canPickGallery = true,
            canTakePhoto = true,
        )
    }
}

private fun openFileInput(
    capture: Boolean,
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/*"
    if (capture) {
        input.setAttribute("capture", "environment")
    }
    input.onchange = {
        val file = input.files?.item(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                runCatching {
                    val buffer = reader.result as ArrayBuffer
                    val view = Int8Array(buffer)
                    ByteArray(view.length) { index -> view[index] }
                }.onSuccess(onImagePicked)
                    .onFailure { onError(it.message ?: "Could not read image") }
            }
            reader.onerror = { onError("Could not read image") }
            reader.readAsArrayBuffer(file)
        }
    }
    input.click()
}
