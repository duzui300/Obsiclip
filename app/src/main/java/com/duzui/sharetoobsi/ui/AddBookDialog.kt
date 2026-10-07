package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.BookDraft
import com.duzui.sharetoobsi.data.BookEntity

/**
 * Adds or edits something being read: title, author, year, and nothing about where its
 * quotes go.
 *
 * The skeleton switch belongs here because a skeleton is a book's note. It is offered only
 * when creating, and writes to wherever the currently chosen target would put this book —
 * shown below the switch so there is no guessing about which note it will touch.
 */
@Composable
fun AddBookDialog(
    existing: BookEntity?,
    deriveSkeletonPath: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (BookDraft) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var author by remember { mutableStateOf(existing?.author.orEmpty()) }
    var year by remember { mutableStateOf(existing?.year.orEmpty()) }
    var createSkeleton by remember { mutableStateOf(false) }

    val skeletonPath = if (createSkeleton) deriveSkeletonPath(title) else ""
    // Hoisted: stringResource is composable, and these are read from a lambda.
    val willWrite = stringResource(R.string.add_book_will_write)
    val fillTitleFirst = stringResource(R.string.add_book_fill_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.add_book_new else R.string.add_book_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.add_book_title)) },
                    supportingText = { Text(stringResource(R.string.add_book_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.add_book_author)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text(stringResource(R.string.add_book_year)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (existing == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.add_book_skeleton),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.add_book_skeleton_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = createSkeleton, onCheckedChange = { createSkeleton = it })
                    }
                    if (createSkeleton) {
                        Text(
                            willWrite + skeletonPath.ifBlank { fillTitleFirst },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        BookDraft(
                            rowId = existing?.id,
                            title = title,
                            author = author,
                            year = year,
                            createSkeleton = createSkeleton,
                        )
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
