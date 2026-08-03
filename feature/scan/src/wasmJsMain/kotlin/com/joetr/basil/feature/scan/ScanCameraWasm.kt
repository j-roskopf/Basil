package com.joetr.basil.feature.scan

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun ScanCamera(
    onCapture: (ScanCaptureResult) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Text("Camera not available on web")
}
