package com.joetr.basil.feature.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberMelaFilePicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): MelaFilePickerControls {
    val context = LocalContext.current
    val onFilePickedState = rememberUpdatedState(onFilePicked)
    val onErrorState = rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read selected file")
        }.onSuccess(onFilePickedState.value)
            .onFailure { onErrorState.value(it.message ?: "Could not read selected file") }
    }

    return MelaFilePickerControls(
        pickFile = {
            launcher.launch(
                arrayOf(
                    "application/zip",
                    "application/octet-stream",
                    "application/json",
                    "*/*",
                ),
            )
        },
    )
}
