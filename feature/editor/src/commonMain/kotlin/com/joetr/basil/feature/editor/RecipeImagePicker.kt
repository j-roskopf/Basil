package com.joetr.basil.feature.editor

import androidx.compose.runtime.Composable

public data class RecipeImagePickerControls(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
    val canPickGallery: Boolean,
    val canTakePhoto: Boolean,
)

@Composable
internal expect fun rememberRecipeImagePicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit = {},
): RecipeImagePickerControls
