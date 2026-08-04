package com.joetr.basil.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.joetr.basil.ui.components.BasilConfirmDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.joetr.basil.domain.model.SessionState
import com.joetr.basil.domain.model.SyncStatus
import com.joetr.basil.domain.model.ThemeMode
import com.joetr.basil.domain.repository.SyncRepository
import com.joetr.basil.domain.usecase.ObserveSessionUseCase
import com.joetr.basil.domain.usecase.ObserveSyncStateUseCase
import com.joetr.basil.domain.usecase.ObserveThemeModeUseCase
import com.joetr.basil.domain.usecase.SetThemeModeUseCase
import com.joetr.basil.domain.usecase.SignOutUseCase
import com.joetr.basil.platform.BasilBuildInfo
import com.joetr.basil.updates.AppUpdateService
import com.joetr.basil.updates.AppUpdateState
import com.joetr.basil.ui.components.BasilSheetColors
import com.joetr.basil.ui.components.BasilSheetScaffold
import com.joetr.basil.ui.components.SheetChip
import com.joetr.basil.ui.components.SheetDivider
import com.joetr.basil.ui.components.SheetPillButton
import com.joetr.basil.ui.components.SheetSectionLabel
import com.joetr.basil.ui.components.SheetTitle
import com.joetr.basil.ui.theme.BasilRadii
import com.joetr.basil.ui.theme.BasilSpacing
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class SettingsViewModel(
    observeSession: ObserveSessionUseCase,
    observeSyncState: ObserveSyncStateUseCase,
    private val syncRepository: SyncRepository,
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val signOutUseCase: SignOutUseCase,
    public val updates: AppUpdateService,
) {
    public val session = observeSession()
    public val syncState = observeSyncState()
    public val themeMode = observeThemeMode()

    public suspend fun retrySync() = syncRepository.retryFailed()

    public suspend fun dropPendingSync() = syncRepository.dropPendingSync()

    public suspend fun dropPendingSyncEntry(id: String) = syncRepository.dropPendingSyncEntry(id)

    public suspend fun setThemeMode(mode: ThemeMode) = setThemeModeUseCase(mode)

    public suspend fun signOut() = signOutUseCase()
}

@Composable
public fun AccountScreen(
    viewModel: SettingsViewModel,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.session.collectAsState(initial = null)
    val sync by viewModel.syncState.collectAsState(initial = null)
    val themeMode by viewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val updateState by viewModel.updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }

    BasilSheetScaffold(modifier) { colors ->
        SheetTitle("Account", colors)

        SheetSectionLabel("Appearance", colors)
        Spacer(Modifier.height(BasilSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm),
        ) {
            ThemeMode.entries.forEach { mode ->
                SheetChip(
                    text = mode.label(),
                    selected = themeMode == mode,
                    colors = colors,
                    onClick = { scope.launch { viewModel.setThemeMode(mode) } },
                )
            }
        }

        SheetDivider(colors)

        SheetSectionLabel("App", colors)
        Spacer(Modifier.height(BasilSpacing.sm))
        Text(
            "v${BasilBuildInfo.versionName}",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.mutedOnSheet,
        )
        UpdateStatusRow(
            updateState = updateState,
            colors = colors,
            onCheck = { scope.launch { viewModel.updates.checkForUpdates() } },
            onInstall = { scope.launch { viewModel.updates.installAvailableUpdate() } },
        )

        SheetDivider(colors)

        SheetSectionLabel("Session", colors)
        Spacer(Modifier.height(BasilSpacing.sm))
        Text(
            sessionLabel(session),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.mutedOnSheet,
        )
        if (session is SessionState.Authenticated) {
            SheetPillButton(
                text = "Sign out",
                colors = colors,
                onClick = { showSignOutConfirm = true },
                modifier = Modifier.padding(top = BasilSpacing.md),
            )
        } else {
            SheetPillButton(
                text = "Sign in",
                colors = colors,
                onClick = onSignIn,
                modifier = Modifier.padding(top = BasilSpacing.md),
            )
        }

        SheetDivider(colors)

        SheetSectionLabel("Sync", colors)
        Spacer(Modifier.height(BasilSpacing.sm))
        sync?.let {
            Text(
                syncStatusLabel(it.status, it.pendingCount),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSheet,
            )
            it.errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
            SheetPillButton(
                text = "Retry sync",
                colors = colors,
                onClick = { scope.launch(Dispatchers.Default) { viewModel.retrySync() } },
                modifier = Modifier.padding(top = BasilSpacing.md),
            )
            if (it.status == SyncStatus.ERROR) {
                SheetPillButton(
                    text = "Drop all pending changes",
                    colors = colors,
                    onClick = { scope.launch { viewModel.dropPendingSync() } },
                    tonal = true,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
        }

        sync?.pendingEntries?.takeIf { it.isNotEmpty() }?.let { entries ->
            Spacer(Modifier.height(BasilSpacing.lg))
            SheetSectionLabel("Pending", colors)
            Spacer(Modifier.height(BasilSpacing.sm))
            entries.forEach { entry ->
                PendingSyncRow(
                    title = entry.title,
                    subtitle = "${entry.kind} · ${entry.id}",
                    colors = colors,
                    onDrop = { scope.launch { viewModel.dropPendingSyncEntry(entry.id) } },
                )
            }
        }
    }

    if (showSignOutConfirm) {
        BasilConfirmDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = "Sign out?",
            message = "This clears all recipes stored on this device and starts a fresh anonymous session.",
            confirmText = "Sign out",
            onConfirm = {
                showSignOutConfirm = false
                scope.launch { viewModel.signOut() }
            },
            destructive = true,
        )
    }
}

@Composable
private fun PendingSyncRow(
    title: String,
    subtitle: String,
    colors: BasilSheetColors,
    onDrop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSheet,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedOnSheet,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(BasilRadii.chip))
                .background(colors.onSheet.copy(alpha = 0.14f))
                .clickable(onClick = onDrop)
                .padding(horizontal = BasilSpacing.md, vertical = 6.dp),
        ) {
            Text("Drop", color = colors.onSheet, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun sessionLabel(session: SessionState?): String = when (session) {
    is SessionState.Authenticated -> "Signed in as ${session.email ?: session.userId}"
    is SessionState.Anonymous -> "Anonymous session"
    is SessionState.LocalPending -> "Local mode (${session.deviceOwnerId})"
    null -> "Loading…"
}

private fun syncStatusLabel(status: SyncStatus, pendingCount: Int): String = when (status) {
    SyncStatus.SYNCED -> "Synced"
    SyncStatus.SYNCING -> "Syncing…"
    SyncStatus.PENDING -> "$pendingCount pending"
    SyncStatus.ERROR -> "Sync error"
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun UpdateStatusRow(
    updateState: AppUpdateState,
    colors: BasilSheetColors,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
) {
    val rowModifier = Modifier.padding(top = BasilSpacing.md)
    when (updateState) {
        AppUpdateState.Idle -> {
            SheetPillButton(
                text = "Check for updates",
                colors = colors,
                onClick = onCheck,
                modifier = rowModifier,
            )
        }
        AppUpdateState.Checking -> {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm),
            ) {
                SheetPillButton(
                    text = "Check for updates",
                    colors = colors,
                    onClick = {},
                    enabled = false,
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.mutedOnSheet,
                )
            }
        }
        AppUpdateState.Current -> {
            Text(
                "Basil is up to date",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedOnSheet,
                modifier = rowModifier,
            )
        }
        is AppUpdateState.Available -> {
            SheetPillButton(
                text = "Update to v${updateState.update.versionName}",
                colors = colors,
                onClick = onInstall,
                modifier = rowModifier,
            )
        }
        is AppUpdateState.Installing -> {
            val downloadProgress = updateState.progress?.takeIf { it < 1f }
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.mutedOnSheet,
                    progress = { downloadProgress ?: 1f },
                )
                Column {
                    Text(
                        updateState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedOnSheet,
                    )
                    downloadProgress?.let { progress ->
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedOnSheet,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        is AppUpdateState.Failed -> {
            Column(modifier = rowModifier) {
                Text(
                    updateState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SheetPillButton(
                    text = "Try again",
                    colors = colors,
                    onClick = onCheck,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
        }
    }
}
