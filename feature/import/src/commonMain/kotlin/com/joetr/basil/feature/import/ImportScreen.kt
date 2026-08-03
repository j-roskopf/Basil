package com.joetr.basil.feature.import

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joetr.basil.domain.repository.ImportHistoryEntry
import com.joetr.basil.domain.usecase.ExportRecipesUseCase
import com.joetr.basil.domain.usecase.ImportBasilRecipesUseCase
import com.joetr.basil.domain.usecase.ImportRecipeFromUrlUseCase
import com.joetr.basil.domain.usecase.ImportMelaRecipesUseCase
import com.joetr.basil.domain.usecase.ObserveImportHistoryUseCase
import com.joetr.basil.navigation.toEditorJson
import com.joetr.basil.platform.openUrl
import com.joetr.basil.platform.readClipboardText
import com.joetr.basil.ui.components.BasilSheetScaffold
import com.joetr.basil.ui.components.SheetDivider
import com.joetr.basil.ui.components.SheetPillButton
import com.joetr.basil.ui.components.SheetTextField
import com.joetr.basil.ui.components.SheetTitle
import com.joetr.basil.ui.components.hostFromUrl
import com.joetr.basil.ui.icons.BasilIcon
import com.joetr.basil.ui.icons.BasilIconPainter
import com.joetr.basil.ui.icons.BasilIcons
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.launch

public class ImportViewModel(
    private val importRecipeFromUrl: ImportRecipeFromUrlUseCase,
    private val importMelaRecipes: ImportMelaRecipesUseCase,
    private val importBasilRecipes: ImportBasilRecipesUseCase,
    private val exportRecipes: ExportRecipesUseCase,
    observeImportHistory: ObserveImportHistoryUseCase,
) {
    public val history = observeImportHistory()

    public suspend fun importUrl(url: String) = importRecipeFromUrl(url)

    public suspend fun importMelaArchive(bytes: ByteArray): ImportMelaRecipesUseCase.Result = importMelaRecipes(bytes)

    public suspend fun importBasilBackup(bytes: ByteArray): ImportBasilRecipesUseCase.Result = importBasilRecipes(bytes)

    public suspend fun exportAllRecipes(): ExportRecipesUseCase.Result = exportRecipes()
}

@Composable
public fun ImportScreen(
    viewModel: ImportViewModel,
    onExtracted: (String) -> Unit,
    onScan: (() -> Unit)? = null,
    initialUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }
    var showUrlPanel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("Extracting recipe…") }
    var melaImportMessage by remember { mutableStateOf<String?>(null) }
    var basilImportMessage by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var pendingExportCount by remember { mutableStateOf<Int?>(null) }
    val history by viewModel.history.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val basilImportPicker = rememberBasilBackupImportPicker(
        onFilePicked = { bytes ->
            if (isLoading) return@rememberBasilBackupImportPicker
            scope.launch {
                isLoading = true
                loadingMessage = "Importing Basil backup…"
                error = null
                basilImportMessage = null
                runCatching { viewModel.importBasilBackup(bytes) }
                    .onSuccess { result ->
                        basilImportMessage = when {
                            result.failed > 0 ->
                                "Imported ${result.saved} of ${result.parsed} recipes (${result.failed} failed)."
                            else ->
                                "Imported ${result.saved} recipes from backup."
                        }
                    }
                    .onFailure { error = it.message }
                isLoading = false
            }
        },
        onError = { message -> error = message },
    )
    val basilExportSaver = rememberBasilBackupExportSaver(
        onSaved = {
            pendingExportCount?.let { count ->
                exportMessage = "Exported $count recipes."
            }
            pendingExportCount = null
        },
        onError = { message -> error = message },
    )
    val melaFilePicker = rememberMelaFilePicker(
        onFilePicked = { bytes ->
            if (isLoading) return@rememberMelaFilePicker
            scope.launch {
                isLoading = true
                loadingMessage = "Importing recipes from Mela…"
                error = null
                melaImportMessage = null
                runCatching { viewModel.importMelaArchive(bytes) }
                    .onSuccess { result ->
                        melaImportMessage = when {
                            result.failed > 0 ->
                                "Imported ${result.saved} of ${result.parsed} recipes from Mela (${result.failed} failed)."
                            else ->
                                "Imported ${result.saved} recipes from Mela."
                        }
                    }
                    .onFailure { error = it.message }
                isLoading = false
            }
        },
        onError = { message -> error = message },
    )

    LaunchedEffect(initialUrl) {
        val prefilled = initialUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: readClipboardText()
                ?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        prefilled?.let {
            url = it
            showUrlPanel = true
        }
    }

    fun extract() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            loadingMessage = "Extracting recipe…"
            error = null
            runCatching { viewModel.importUrl(url.trim()) }
                .onSuccess { onExtracted(it.toEditorJson()) }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        BasilSheetScaffold(modifier) { colors ->
            SheetTitle("Import", colors)

            ImportActionRow(
                label = "Import from URL",
                icon = BasilIcons.Download,
                tint = colors.onSheet,
                onClick = { showUrlPanel = !showUrlPanel },
            )
            onScan?.let { scan ->
                ImportActionRow(
                    label = "Scan cookbook",
                    icon = BasilIcons.Scan,
                    tint = colors.onSheet,
                    onClick = scan,
                )
            }
            ImportActionRow(
                label = "Import from Mela",
                icon = BasilIcons.Download,
                tint = colors.onSheet,
                onClick = melaFilePicker.pickFile,
            )
            ImportActionRow(
                label = "Import Basil backup",
                icon = BasilIcons.Download,
                tint = colors.onSheet,
                onClick = basilImportPicker.pickFile,
            )
            ImportActionRow(
                label = "Export all recipes",
                icon = BasilIcons.Download,
                tint = colors.onSheet,
                onClick = {
                    if (isLoading) return@ImportActionRow
                    scope.launch {
                        isLoading = true
                        loadingMessage = "Preparing export…"
                        error = null
                        exportMessage = null
                        runCatching { viewModel.exportAllRecipes() }
                            .onSuccess { result ->
                                pendingExportCount = result.recipeCount
                                basilExportSaver.saveFile(result.bytes)
                            }
                            .onFailure { error = it.message }
                        isLoading = false
                    }
                },
            )
            ImportActionRow(
                label = "Open in browser",
                icon = BasilIcons.Globe,
                tint = colors.onSheet,
                onClick = { openUrl(url.ifBlank { "https://www.google.com" }) },
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
            melaImportMessage?.let {
                Text(
                    it,
                    color = colors.onSheet,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
            basilImportMessage?.let {
                Text(
                    it,
                    color = colors.onSheet,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
            exportMessage?.let {
                Text(
                    it,
                    color = colors.onSheet,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }

            AnimatedVisibility(visible = showUrlPanel) {
                Column(Modifier.padding(top = BasilSpacing.md, bottom = BasilSpacing.sm)) {
                    SheetTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = "https://…",
                        colors = colors,
                    )
                    SheetPillButton(
                        text = if (isLoading) "Extracting…" else "Extract recipe",
                        colors = colors,
                        onClick = ::extract,
                        modifier = Modifier.padding(top = BasilSpacing.md),
                    )
                }
            }

            SheetDivider(colors)

            if (history.isNotEmpty()) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.mutedOnSheet,
                )
                Spacer(Modifier.height(BasilSpacing.sm))
                history.forEach { entry ->
                    ImportHistoryRow(
                        entry = entry,
                        tint = colors.onSheet,
                        muted = colors.mutedOnSheet,
                        onClick = {
                            url = entry.url
                            showUrlPanel = true
                        },
                    )
                }
            } else {
                Text(
                    "Paste a recipe link or scan a cookbook page.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mutedOnSheet,
                )
            }
        }

        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        loadingMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(top = BasilSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportActionRow(
    label: String,
    icon: BasilIconPainter,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
            color = tint,
        )
        BasilIcon(icon, tint = tint, size = 22.dp)
    }
}

@Composable
private fun ImportHistoryRow(
    entry: ImportHistoryEntry,
    tint: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.title ?: "Untitled",
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                maxLines = 1,
            )
            Text(
                hostFromUrl(entry.url) ?: entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
            )
        }
        BasilIcon(BasilIcons.Chevron, tint = muted, size = 18.dp)
    }
}
