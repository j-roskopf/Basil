package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable

internal class BasilBackupImportPickerControls(
    val pickFile: () -> Unit,
)

internal class BasilBackupExportSaverControls(
    val saveFile: (ByteArray) -> Unit,
)

@Composable
internal expect fun rememberBasilBackupImportPicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): BasilBackupImportPickerControls

@Composable
internal expect fun rememberBasilBackupExportSaver(
    onSaved: () -> Unit,
    onError: (String) -> Unit,
): BasilBackupExportSaverControls
