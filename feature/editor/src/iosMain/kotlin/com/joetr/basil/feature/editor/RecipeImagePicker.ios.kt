package com.joetr.basil.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

private var activePickerDelegate: ImagePickerDelegate? = null

@Composable
internal actual fun rememberRecipeImagePicker(
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
): RecipeImagePickerControls {
    return remember {
        RecipeImagePickerControls(
            pickFromGallery = {
                presentPicker(
                    source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary,
                    onImagePicked = onImagePicked,
                    onError = onError,
                )
            },
            takePhoto = {
                val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                if (!UIImagePickerController.isSourceTypeAvailable(camera)) {
                    onError("Camera is not available on this device")
                    return@RecipeImagePickerControls
                }
                presentPicker(
                    source = camera,
                    onImagePicked = onImagePicked,
                    onError = onError,
                )
            },
            canPickGallery = UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary,
            ),
            canTakePhoto = UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
            ),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun presentPicker(
    source: UIImagePickerControllerSourceType,
    onImagePicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: run {
            onError("Could not open image picker")
            return
        }
    val picker = UIImagePickerController()
    picker.sourceType = source
    picker.allowsEditing = true
    val delegate = ImagePickerDelegate(
        onPicked = { image ->
            val data = UIImageJPEGRepresentation(image, 0.9)
            if (data == null) {
                onError("Could not encode photo")
            } else {
                onImagePicked(data.toByteArray())
            }
            activePickerDelegate = null
            picker.dismissViewControllerAnimated(true, completion = null)
        },
        onCancel = {
            activePickerDelegate = null
            picker.dismissViewControllerAnimated(true, completion = null)
        },
    )
    activePickerDelegate = delegate
    picker.delegate = delegate
    root.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size == 0) return out
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

private class ImagePickerDelegate(
    private val onPicked: (UIImage) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = (
            didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]
            ) as? UIImage
        if (image != null) onPicked(image) else onCancel()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        onCancel()
    }
}
