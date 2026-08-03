package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@Composable
internal actual fun rememberMelaFilePicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): MelaFilePickerControls {
    return remember {
        MelaFilePickerControls(
            pickFile = {
                val input = document.createElement("input") as HTMLInputElement
                input.type = "file"
                input.accept = ".melarecipes,.melarecipe,.zip,application/zip,application/json"
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
