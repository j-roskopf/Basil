package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable

internal class MelaFilePickerControls(
    val pickFile: () -> Unit,
)

@Composable
internal expect fun rememberMelaFilePicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): MelaFilePickerControls
