package com.joetr.basil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joetr.basil.updates.AvailableUpdate

@Composable
internal actual fun UpdateInstallConfirmationDialog(
    update: AvailableUpdate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Install Basil ${update.versionName}?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A new version is ready.", fontSize = 14.sp)
                Text(
                    "Refresh Basil to load the latest web build.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Refresh now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
