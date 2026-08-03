package com.joetr.basil.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
                "Update to Basil ${update.versionName}?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A newer version is available.", fontSize = 14.sp)
                Text(
                    "Basil will open the download page or system installer.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
