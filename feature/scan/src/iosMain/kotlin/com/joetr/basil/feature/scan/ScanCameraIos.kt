package com.joetr.basil.feature.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.joetr.basil.ui.components.PillButton
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun ScanCamera(
    onCapture: (ScanCaptureResult) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val controller = remember { IosScanController() }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }

    Box(modifier.fillMaxSize()) {
        UIKitView(
            factory = { controller.previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                controller.previewView.setFrame(view.bounds)
            },
        )
        PillButton(
            "Capture & scan",
            onClick = {
                if (!enabled) return@PillButton
                scope.launch {
                    runCatching { controller.captureAndRecognize() }
                        .onSuccess { result ->
                            if (result.ocrText.isBlank()) onError("No text detected — try better lighting")
                            else onCapture(result)
                        }
                        .onFailure { onError(it.message ?: "OCR failed") }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(BasilSpacing.xl),
        )
    }
}
