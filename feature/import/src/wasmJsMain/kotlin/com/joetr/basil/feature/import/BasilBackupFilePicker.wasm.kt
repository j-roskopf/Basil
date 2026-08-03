package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joetr.basil.domain.export.BasilRecipeCodec
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.Blob
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
internal actual fun rememberBasilBackupImportPicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): BasilBackupImportPickerControls {
    return remember {
        BasilBackupImportPickerControls(
            pickFile = {
                val input = document.createElement("input") as HTMLInputElement
                input.type = "file"
                input.accept = ".${BasilRecipeCodec.FILE_EXTENSION},.json,application/json"
                input.onchange = {
                    val file = input.files?.item(0)
                    if (file != null) {
                        val reader = FileReader()
                        reader.onload = {
                            runCatching {
                                val buffer = reader.result as ArrayBuffer
                                val view = Int8Array(buffer)
                                ByteArray(view.length) { index -> view[index] }
                            }.onSuccess(onFilePicked)
                                .onFailure { onError(it.message ?: "Could not read file") }
                        }
                        reader.onerror = { onError("Could not read file") }
                        reader.readAsArrayBuffer(file)
                    }
                }
                input.click()
            },
        )
    }
}

@Composable
internal actual fun rememberBasilBackupExportSaver(
    onSaved: () -> Unit,
    onError: (String) -> Unit,
): BasilBackupExportSaverControls {
    return remember {
        BasilBackupExportSaverControls(
            saveFile = { bytes ->
                runCatching {
                    val blob = Blob(arrayOf(bytes))
                    val url = org.w3c.dom.url.URL.createObjectURL(blob)
                    val anchor = document.createElement("a") as HTMLAnchorElement
                    anchor.href = url
                    anchor.download = "basil-recipes.${BasilRecipeCodec.FILE_EXTENSION}"
                    anchor.click()
                    org.w3c.dom.url.URL.revokeObjectURL(url)
                    onSaved()
                }.onFailure { onError(it.message ?: "Could not save backup file") }
            },
        )
    }
}
