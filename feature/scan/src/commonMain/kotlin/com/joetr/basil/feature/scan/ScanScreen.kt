package com.joetr.basil.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.repository.ImageRepository
import com.joetr.basil.domain.usecase.ScanRecipeFromImageUseCase
import com.joetr.basil.navigation.toEditorJson
import com.joetr.basil.platform.ImageCapture
import com.joetr.basil.ui.components.PillButton
import com.joetr.basil.ui.layout.basilSafeArea
import com.joetr.basil.ui.theme.BasilSpacing
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

public class ScanViewModel(
    private val scanRecipe: ScanRecipeFromImageUseCase,
    private val imageRepository: ImageRepository,
) {
    public suspend fun processCapture(result: ScanCaptureResult): ExtractedRecipe {
        val extracted = scanRecipe(result.ocrText)
        val localImageId = imageRepository.saveLocalImage("scan-pending", result.imageBytes)
        return extracted.copy(localImageId = localImageId)
    }
}

@Composable
public fun ScanScreen(
    viewModel: ScanViewModel,
    onExtracted: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun handleCapture(result: ScanCaptureResult) {
        processing = true
        error = null
        scope.launch {
            runCatching { viewModel.processCapture(result) }
                .onSuccess { onExtracted(it.toEditorJson()) }
                .onFailure { error = it.message }
            processing = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .basilSafeArea(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = BasilSpacing.gutter, vertical = BasilSpacing.lg),
        ) {
        Text(
            "Scan recipe",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!ImageCapture.isAvailable) {
            Text("Scanning is only available on mobile.", style = MaterialTheme.typography.bodyLarge)
            PillButton("Back", onClick = onBack, modifier = Modifier.padding(top = BasilSpacing.lg))
            return@Column
        }
        ScanCamera(
            onCapture = ::handleCapture,
            onError = { error = it },
            enabled = !processing,
            modifier = Modifier.weight(1f),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = BasilSpacing.sm)) }
        PillButton("Back", onClick = onBack, tonal = true, modifier = Modifier.padding(top = BasilSpacing.md))
        }
        if (processing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        "Parsing recipe…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = BasilSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
internal expect fun ScanCamera(
    onCapture: (ScanCaptureResult) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
)
