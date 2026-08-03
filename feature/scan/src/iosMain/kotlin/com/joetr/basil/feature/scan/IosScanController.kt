package com.joetr.basil.feature.scan

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizeTextRequestRevision2
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
internal class IosScanController {
    val previewView: UIView = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    fun start() {
        session.sessionPreset = AVCaptureSessionPresetPhoto
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: return
        if (session.canAddInput(input)) session.addInput(input)
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)

        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.frame = previewView.bounds
        previewView.layer.addSublayer(layer)
        previewLayer = layer

        dispatch_async(dispatch_get_main_queue()) {
            session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(dispatch_get_main_queue()) {
            session.stopRunning()
        }
    }

    suspend fun captureAndRecognize(): ScanCaptureResult = suspendCancellableCoroutine { continuation ->
        val delegate = PhotoDelegate(
            onPhoto = { data ->
                if (data == null) {
                    continuation.resumeWithException(IllegalStateException("Capture failed"))
                    return@PhotoDelegate
                }
                runCatching {
                    val text = recognizeText(data)
                    val bytes = data.toByteArray()
                    ScanCaptureResult(ocrText = text, imageBytes = bytes)
                }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
        )
        val settings = AVCapturePhotoSettings.photoSettings()
        photoOutput.capturePhotoWithSettings(settings, delegate)
        continuation.invokeOnCancellation { delegate.markCancelled() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun recognizeText(data: NSData): String {
        val image = UIImage(data = data) ?: error("Invalid image data")
        val cgImage = image.CGImage ?: error("Could not read captured image")
        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.usesLanguageCorrection = true
        request.revision = VNRecognizeTextRequestRevision2

        val handler = VNImageRequestHandler(cgImage, options = emptyMap<Any?, Any?>())
        handler.performRequests(listOf(request), error = null)

        val observations = request.results.orEmpty().mapNotNull { it as? VNRecognizedTextObservation }
        return observations.mapNotNull { observation ->
            val candidate = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText
            candidate?.string
        }.joinToString("\n")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PhotoDelegate(
    private val onPhoto: (NSData?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    private var cancelled = false

    fun markCancelled() {
        cancelled = true
    }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        if (cancelled) return
        if (error != null) {
            onPhoto(null)
            return
        }
        onPhoto(didFinishProcessingPhoto.fileDataRepresentation())
    }
}
