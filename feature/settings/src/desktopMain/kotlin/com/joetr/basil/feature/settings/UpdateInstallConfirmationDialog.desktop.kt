package com.joetr.basil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.joetr.basil.ui.theme.BasilSpacing
import com.joetr.basil.updates.AvailableUpdate

@Composable
internal actual fun UpdateInstallConfirmationDialog(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(width = 440.dp, height = 260.dp),
        title = "Install Basil ${update.versionName}",
        resizable = false,
        alwaysOnTop = true,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(BasilSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(BasilSpacing.md),
            ) {
                Text(
                    "Install Basil ${update.versionName}?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "The update has been downloaded and verified.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Basil will close, replace the installed app, and relaunch automatically. " +
                        "Your operating system may ask for permission if Basil is installed in a protected location.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm, Alignment.End),
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("Later") }
                    Button(onClick = onConfirm) {
                        Text("Restart and install", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
