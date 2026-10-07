package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.ImportEntry

/**
 * What was read off Kindle's notebook, before any of it is written.
 *
 * The review step is not ceremony. Kindle collapses long highlights in that list, and a
 * collapsed highlight looks exactly like a short one — so a fragment can arrive with nothing
 * to mark it as one. Ticking and editing here is what keeps that from being written silently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewScreen(
    entries: List<ImportEntry>,
    targetPath: String,
    onToggle: (Int, Boolean) -> Unit,
    onEdit: (Int, String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onWrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = entries.count { it.included }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.import_review_discard))
                    }
                },
            )
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.import_review_summary, entries.size, chosen),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(targetPath, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSelectAll(true) }) { Text(stringResource(R.string.import_review_select_writable)) }
                    TextButton(onClick = { onSelectAll(false) }) { Text(stringResource(R.string.import_review_select_none)) }
                }
                Text(
                    stringResource(R.string.import_review_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(entries) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = entry.included,
                            onCheckedChange = { onToggle(index, it) },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutlinedTextField(
                                value = entry.text,
                                onValueChange = { onEdit(index, it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp, max = 200.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            val note = when {
                                entry.suspect -> stringResource(R.string.import_review_truncated)
                                entry.alreadyImported -> stringResource(R.string.import_review_already)
                                else -> null
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                entry.page?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                note?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            Button(
                onClick = onWrite,
                enabled = chosen > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(if (chosen > 0) "写入 $chosen 条" else "没有勾选任何条目")
            }
        }
    }
}
