package com.joetr.basil.feature.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.joetr.basil.domain.export.BasilRecipeCodec

@Composable
internal actual fun rememberBasilBackupImportPicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): BasilBackupImportPickerControls {
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

    return remember {
        BasilBackupImportPickerControls(
            pickFile = {
                launcher.launch(
                    arrayOf(
                        "application/json",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
        )
    }
}

@Composable
internal actual fun rememberBasilBackupExportSaver(
    onSaved: () -> Unit,
    onError: (String) -> Unit,
): BasilBackupExportSaverControls {
    val context = LocalContext.current
    val onErrorState = rememberUpdatedState(onError)
    val onSavedState = rememberUpdatedState(onSaved)
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not write backup file")
        }.onSuccess { onSavedState.value() }
            .onFailure { onErrorState.value(it.message ?: "Could not save backup file") }
    }

    return remember {
        BasilBackupExportSaverControls(
            saveFile = { bytes ->
                pendingBytes = bytes
                launcher.launch("basil-recipes.${BasilRecipeCodec.FILE_EXTENSION}")
            },
        )
    }
}
