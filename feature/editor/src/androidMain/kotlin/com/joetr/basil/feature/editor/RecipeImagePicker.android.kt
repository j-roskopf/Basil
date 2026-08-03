package com.joetr.basil.feature.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
internal actual fun rememberRecipeImagePicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): RecipeImagePickerControls {
    val context = LocalContext.current
    val onImagePickedState by rememberUpdatedState(onImagePicked)
    val onErrorState by rememberUpdatedState(onError)
    var pendingCamera by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read selected image")
        }.onSuccess(onImagePickedState)
            .onFailure { onErrorState(it.message ?: "Could not read selected image") }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (!success || uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read captured photo")
        }.onSuccess(onImagePickedState)
            .onFailure { onErrorState(it.message ?: "Could not read captured photo") }
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingCamera) {
            launchCameraCapture(
                context = context,
                onUriReady = { pendingCaptureUri = it },
                onLaunch = cameraLauncher::launch,
                onError = onErrorState,
            )
        } else if (!granted) {
            onErrorState("Camera permission is required to take a photo")
        }
        pendingCamera = false
    }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture(
                context = context,
                onUriReady = { pendingCaptureUri = it },
                onLaunch = cameraLauncher::launch,
                onError = onErrorState,
            )
        } else {
            pendingCamera = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return RecipeImagePickerControls(
        pickFromGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        takePhoto = ::takePhoto,
        canPickGallery = true,
        canTakePhoto = true,
    )
}

private fun launchCameraCapture(
    context: android.content.Context,
    onUriReady: (Uri) -> Unit,
    onLaunch: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    runCatching {
        val photoFile = File(context.cacheDir, "recipe_capture_${System.currentTimeMillis()}.jpg")
        photoFile.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
        onUriReady(uri)
        onLaunch(uri)
    }.onFailure {
        onError(it.message ?: "Could not open camera")
    }
}
