package com.joetr.basil.feature.settings

import androidx.compose.runtime.Composable
import com.joetr.basil.updates.AvailableUpdate

@Composable
internal expect fun UpdateInstallConfirmationDialog(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
)

@Composable
public fun AppUpdateInstallDialog(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    UpdateInstallConfirmationDialog(update, onDismiss, onConfirm)
}
