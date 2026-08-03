package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.posix.memcpy

private var activeMelaPickerDelegate: MelaDocumentPickerDelegate? = null

@Composable
internal actual fun rememberMelaFilePicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): MelaFilePickerControls {
    val onFilePickedState = rememberUpdatedState(onFilePicked)
    val onErrorState = rememberUpdatedState(onError)

    return remember {
        MelaFilePickerControls(
            pickFile = {
                val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                    ?: run {
                        onErrorState.value("Could not open file picker")
                        return@MelaFilePickerControls
                    }
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf(
                        "public.zip-archive",
                        "public.json",
                        "public.data",
                    ),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                val delegate = MelaDocumentPickerDelegate(
                    onPicked = { bytes -> onFilePickedState.value(bytes) },
                    onError = { message -> onErrorState.value(message) },
                    onFinished = { activeMelaPickerDelegate = null },
                )
                activeMelaPickerDelegate = delegate
                picker.delegate = delegate
                root.presentViewController(picker, animated = true, completion = null)
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class MelaDocumentPickerDelegate(
    private val onPicked: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onFinished: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        controller.dismissViewControllerAnimated(true, completion = null)
        onFinished()
        if (url == null) {
            onError("No file selected")
            return
        }
        val accessed = url.startAccessingSecurityScopedResource()
        val path = url.path
        val data = path?.let { NSFileManager.defaultManager.contentsAtPath(it) }
        if (accessed) url.stopAccessingSecurityScopedResource()
        if (data == null) {
            onError("Could not read selected file")
            return
        }
        onPicked(data.toByteArray())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        controller.dismissViewControllerAnimated(true, completion = null)
        onFinished()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}
