package com.zyn.chat.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zyn.chat.app.R
import com.zyn.chat.app.ui.theme.accent

/**
 * Renames a conversation.
 *
 * The dialog only collects the new title; a blank one is rejected by the confirm button, and the
 * stored title is what the drawer reads afterwards.
 *
 * @param initialTitle The conversation's current title, pre-filled for editing.
 * @param onConfirm Invoked with the new title when confirmed with a non-blank value.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
@Composable
fun RenameConversationDialog(
    initialTitle: String,
    onConfirm: (title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zyn_conversation_rename)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.zyn_conversation_rename_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.filterNot(Char::isISOControl)) },
                enabled = title.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.zyn_conversation_rename_confirm),
                    color = MaterialTheme.accent.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zyn_conversation_rename_cancel))
            }
        },
    )
}

/**
 * Confirms deleting a conversation and everything it asked.
 *
 * @param title The conversation's title, shown in the prompt.
 * @param onConfirm Invoked when the deletion is confirmed.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
@Composable
fun DeleteConversationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zyn_conversation_delete_title)) },
        text = { Text(stringResource(R.string.zyn_conversation_delete_message, title)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.zyn_conversation_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zyn_conversation_rename_cancel))
            }
        },
    )
}
