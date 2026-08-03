package com.joetr.basil.feature.import

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.posix.memcpy

private var activeBasilImportPickerDelegate: BasilImportDocumentPickerDelegate? = null

@Composable
internal actual fun rememberBasilBackupImportPicker(
    onFilePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): BasilBackupImportPickerControls {
    val onFilePickedState = rememberUpdatedState(onFilePicked)
    val onErrorState = rememberUpdatedState(onError)

    return remember {
        BasilBackupImportPickerControls(
            pickFile = {
                val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                    ?: run {
                        onErrorState.value("Could not open file picker")
                        return@BasilBackupImportPickerControls
                    }
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf("public.json", "public.data"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                val delegate = BasilImportDocumentPickerDelegate(
                    onPicked = { bytes -> onFilePickedState.value(bytes) },
                    onError = { message -> onErrorState.value(message) },
                    onFinished = { activeBasilImportPickerDelegate = null },
                )
                activeBasilImportPickerDelegate = delegate
                picker.delegate = delegate
                root.presentViewController(picker, animated = true, completion = null)
            },
        )
    }
}

@Composable
internal actual fun rememberBasilBackupExportSaver(
    onSaved: () -> Unit,
    onError: (String) -> Unit,
): BasilBackupExportSaverControls {
    val onErrorState = rememberUpdatedState(onError)
    val onSavedState = rememberUpdatedState(onSaved)

    return remember {
        BasilBackupExportSaverControls(
            saveFile = { bytes ->
                val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                    ?: run {
                        onErrorState.value("Could not open save dialog")
                        return@BasilBackupExportSaverControls
                    }
                val cacheDir = NSSearchPathForDirectoriesInDomains(
                    NSCachesDirectory,
                    NSUserDomainMask,
                    true,
                ).firstOrNull() as? String
                if (cacheDir == null) {
                    onErrorState.value("Could not create export file")
                    return@BasilBackupExportSaverControls
                }
                val filePath = "$cacheDir/basil-recipes.basilrecipes"
                val data = bytes.toNSData()
                if (!NSFileManager.defaultManager.createFileAtPath(filePath, contents = data, attributes = null)) {
                    onErrorState.value("Could not write export file")
                    return@BasilBackupExportSaverControls
                }
                val fileUrl = NSURL.fileURLWithPath(filePath)
                val activity = UIActivityViewController(
                    activityItems = listOf(fileUrl),
                    applicationActivities = null,
                )
                root.presentViewController(activity, animated = true, completion = null)
                onSavedState.value()
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class BasilImportDocumentPickerDelegate(
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
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, this.length)
    }
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    if (isEmpty()) return@memScoped NSData()
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}
