package com.joetr.basil.feature.scan

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageCapture as CameraXImageCapture
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.joetr.basil.platform.rotateJpeg
import com.joetr.basil.ui.components.PillButton
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun ScanCamera(
    onCapture: (ScanCaptureResult) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onError("Camera permission is required to scan recipes")
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    if (!hasPermission) return

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { CameraXImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(previewView) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        PillButton(
            "Capture & scan",
            onClick = {
                if (!enabled) return@PillButton
                imageCapture.takePicture(
                    cameraExecutor,
                    object : CameraXImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            scope.launch {
                                runCatching {
                                    val mediaImage = image.image ?: error("No image buffer")
                                    val rotation = image.imageInfo.rotationDegrees
                                    val jpegBytes = image.toJpegBytes().let { bytes ->
                                        when {
                                            image.format == ImageFormat.YUV_420_888 && rotation != 0 ->
                                                rotateJpeg(bytes, rotation)
                                            else -> bytes
                                        }
                                    }
                                    val input = InputImage.fromMediaImage(mediaImage, rotation)
                                    image.close()
                                    val text = recognizeText(input)
                                    ScanCaptureResult(ocrText = text, imageBytes = jpegBytes)
                                }.onSuccess { result ->
                                    if (result.ocrText.isBlank()) onError("No text detected — try better lighting")
                                    else onCapture(result)
                                }.onFailure { onError(it.message ?: "OCR failed") }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError(exception.message ?: "Capture failed")
                        }
                    },
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(BasilSpacing.xl),
        )
    }
}

private suspend fun recognizeText(image: InputImage): String =
    suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

private fun ImageProxy.toJpegBytes(quality: Int = 90): ByteArray {
    return when (format) {
        ImageFormat.JPEG -> {
            val buffer = planes[0].buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        }
        ImageFormat.YUV_420_888 -> {
            val nv21 = yuv420888ToNv21(this)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
            out.toByteArray()
        }
        else -> error("Unsupported image format: $format")
    }
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    return nv21
}
