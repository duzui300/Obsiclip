package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.duzui.sharetoobsi.TargetDraft
import com.duzui.sharetoobsi.data.FormatEntity
import com.duzui.sharetoobsi.data.TargetEntity

/**
 * Adds or edits a destination: where a capture goes, how it is shaped, and nothing else.
 *
 * What is being read is a separate axis — see [AddBookDialog] — which is what lets one
 * target serve every book instead of needing a row per title.
 */
@Composable
fun AddTargetDialog(
    existing: TargetEntity?,
    defaultPath: String,
    defaultHeading: String,
    formats: List<FormatEntity>,
    onDismiss: () -> Unit,
    onSave: (TargetDraft) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var path by remember { mutableStateOf(existing?.path ?: defaultPath) }
    var heading by remember { mutableStateOf(existing?.heading ?: defaultHeading) }
    var formatId by remember { mutableStateOf(existing?.formatId) }
    var formatMenu by remember { mutableStateOf(false) }

    val formatLabel = formats.firstOrNull { it.id == formatId }?.name ?: stringResource(R.string.add_target_format_label)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.add_target_new else R.string.add_target_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    supportingText = { Text(stringResource(R.string.add_target_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.add_target_path)) },
                    supportingText = {
                        Text(stringResource(R.string.add_target_path_hint))
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = heading,
                    onValueChange = { heading = it },
                    label = { Text(stringResource(R.string.add_target_section)) },
                    supportingText = { Text(stringResource(R.string.add_target_section_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.add_target_format_label), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { formatMenu = true }) { Text(formatLabel) }
                    DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_target_format_label)) },
                            onClick = {
                                formatId = null
                                formatMenu = false
                            },
                        )
                        formats.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format.name) },
                                onClick = {
                                    formatId = format.id
                                    formatMenu = false
                                },
                            )
                        }
                    }
                }
                if (formats.isEmpty()) {
                    Text(
                        stringResource(R.string.add_target_no_formats),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        TargetDraft(
                            rowId = existing?.id,
                            name = name,
                            path = path,
                            heading = heading,
                            formatId = formatId,
                        )
                    )
                },
                enabled = name.isNotBlank() && path.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
