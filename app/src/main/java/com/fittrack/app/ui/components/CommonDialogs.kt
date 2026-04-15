package com.fittrack.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * A generic single-field text-input dialog.
 * Manages its own input state; calls [onConfirm] with the trimmed, non-blank value.
 */
@Composable
fun NameInputDialog(
    title: String,
    label: String,
    confirmText: String = "Erstellen",
    dismissText: String = "Abbrechen",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}

/**
 * A generic delete-confirmation dialog.
 * The caller is responsible for hiding the dialog after [onConfirm] or [onDismiss].
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Löschen",
    dismissText: String = "Abbrechen",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}
