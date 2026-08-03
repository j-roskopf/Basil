package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.joetr.basil.platform.pickDesktopOpenFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberMelaFilePicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): MelaFilePickerControls {
    val scope = rememberCoroutineScope()
    return remember {
        MelaFilePickerControls(
            pickFile = {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            pickDesktopOpenFile(
                                title = "Import Mela recipes",
                                extensions = setOf("melarecipes", "melarecipe", "zip"),
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
