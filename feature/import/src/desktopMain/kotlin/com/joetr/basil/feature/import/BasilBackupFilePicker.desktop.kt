package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.joetr.basil.domain.export.BasilRecipeCodec
import com.joetr.basil.platform.pickDesktopOpenFile
import com.joetr.basil.platform.pickDesktopSaveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberBasilBackupImportPicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): BasilBackupImportPickerControls {
    val scope = rememberCoroutineScope()
    return remember {
        BasilBackupImportPickerControls(
            pickFile = {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            pickDesktopOpenFile(
                                title = "Import Basil backup",
                                extensions = setOf(BasilRecipeCodec.FILE_EXTENSION, "json"),
                            )
                        }.getOrElse {
                            onError(it.message ?: "Could not read file")
                            null
                        }
                    }
                    if (bytes != null) onFilePicked(bytes)
                }
            },
        )
    }
}

@Composable
internal actual fun rememberBasilBackupExportSaver(
    onSaved: () -> Unit,
    onError: (String) -> Unit,
): BasilBackupExportSaverControls {
    val scope = rememberCoroutineScope()
    return remember {
        BasilBackupExportSaverControls(
            saveFile = { bytes ->
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            pickDesktopSaveFile(
                                title = "Export Basil recipes",
                                defaultFileName = "basil-recipes.${BasilRecipeCodec.FILE_EXTENSION}",
                                bytes = bytes,
                                extensions = setOf(BasilRecipeCodec.FILE_EXTENSION),
                            )
                        }.getOrElse {
                            onError(it.message ?: "Could not save file")
                            false
                        }
                    }
                    if (saved) onSaved()
                }
            },
        )
    }
}
