package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.BookDraft
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.ShareUiState
import com.duzui.sharetoobsi.TargetDraft
import com.duzui.sharetoobsi.domain.SourceProfile
import com.duzui.sharetoobsi.domain.SourceProfiles
import com.duzui.sharetoobsi.domain.WriteMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    state: ShareUiState,
    message: String?,
    onProfileChange: (SourceProfile) -> Unit,
    onSelectTarget: (Long?) -> Unit,
    onMoveTarget: (Int, Int) -> Unit,
    onSaveTarget: (TargetDraft) -> Unit,
    onSelectBook: (Long?) -> Unit,
    onMoveBook: (Int, Int) -> Unit,
    onSaveBook: (BookDraft) -> Unit,
    onDeriveSkeletonPath: (String) -> String,
    onReadClipboard: () -> Unit,
    onPayloadEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var addingTarget by remember { mutableStateOf(false) }
    var addingBook by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    if (addingTarget) {
        AddTargetDialog(
            existing = null,
            defaultPath = state.settings.pathTemplate,
            defaultHeading = state.settings.heading,
            formats = state.formats,
            onDismiss = { addingTarget = false },
            onSave = { draft ->
                onSaveTarget(draft)
                addingTarget = false
            },
        )
    }
    if (addingBook) {
        AddBookDialog(
            existing = null,
            deriveSkeletonPath = onDeriveSkeletonPath,
            onDismiss = { addingBook = false },
            onSave = { draft ->
                onSaveBook(draft)
                addingBook = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // First thing under the app bar: the common case is that the text arrived
            // already processed, so sending should never require a scroll.
            Button(
                onClick = onSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.share_send), style = MaterialTheme.typography.titleMedium)
            }

            DestinationSection(
                state = state,
                onSelect = onSelectTarget,
                onMove = onMoveTarget,
                onAdd = { addingTarget = true },
            )

            BookSection(
                state = state,
                onSelect = onSelectBook,
                onMove = onMoveBook,
                onAdd = { addingBook = true },
            )

            if (state.raw.isBlank()) {
                EmptyState(state.origin, onReadClipboard)
            } else {
                PayloadField(state, onPayloadEdit, onRegenerate, onReadClipboard)
            }

            SourcePicker(state, onProfileChange)
        }
    }
}

@Composable
private fun DestinationSection(
    state: ShareUiState,
    onSelect: (Long?) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.share_destination_label), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val reorder = rememberReorderState(state.savedTargets.size)

                state.savedTargets.forEachIndexed { index, target ->
                    FilterChip(
                        selected = state.chosenTarget?.id == target.id,
                        onClick = { onSelect(target.id) },
                        label = { Text(target.name) },
                        modifier = Modifier.reorderDrag(
                            key = target.id,
                            index = index,
                            state = reorder,
                            horizontal = true,
                            onMove = onMove,
                        ),
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onAdd,
                    label = { Text(stringResource(R.string.share_add_short)) },
                )
            }
            Text(state.targetPath, style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    state.settings.mode == WriteMode.OFFICIAL ->
                        stringResource(R.string.share_append_to_end_official)
                    state.effectiveHeading != null -> stringResource(R.string.share_append_to_section, state.effectiveHeading)
                    else -> stringResource(R.string.share_append_to_end)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BookSection(
    state: ShareUiState,
    onSelect: (Long?) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.share_book_label), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val reorder = rememberReorderState(state.savedBooks.size)

                state.savedBooks.forEachIndexed { index, book ->
                    FilterChip(
                        selected = state.chosenBook?.id == book.id,
                        onClick = { onSelect(book.id) },
                        label = { Text(book.title) },
                        modifier = Modifier.reorderDrag(
                            key = book.id,
                            index = index,
                            state = reorder,
                            horizontal = true,
                            onMove = onMove,
                        ),
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onAdd,
                    label = { Text(stringResource(R.string.share_add_short)) },
                )
            }
            val book = state.chosenBook
            Text(
                if (book == null) {
                    stringResource(R.string.share_no_books)
                } else {
                    listOfNotNull(
                        book.author.takeIf { it.isNotBlank() },
                        book.year.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { stringResource(R.string.share_no_author_year) }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyState(origin: String, onReadClipboard: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (origin.isBlank()) stringResource(R.string.share_empty_title) else stringResource(R.string.share_empty_title_origin, origin),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "从别的 App 分享过来，或者在那里选中文字后「复制」，再读进来。\n" +
                    "分享面板有长度限制的 App（比如 Kindle），走复制这条路更稳，也更干净。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onReadClipboard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.share_read_clipboard))
            }
        }
    }
}

@Composable
private fun PayloadField(
    state: ShareUiState,
    onEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
    onReadClipboard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.share_payload_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReadClipboard) { Text(stringResource(R.string.share_reread_clipboard)) }
            if (state.payloadOverride != null) {
                TextButton(onClick = onRegenerate) { Text(stringResource(R.string.share_regenerate)) }
            }
        }
        OutlinedTextField(
            value = state.payload,
            onValueChange = onEdit,
            // Capped: a 15k-character highlight would otherwise grow the field until the
            // send button is dozens of swipes away. Past the cap the field scrolls itself.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 320.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(
            stringResource(R.string.share_origin_chars, state.origin, state.raw.length),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SourcePicker(state: ShareUiState, onProfileChange: (SourceProfile) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.share_rules_label), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { expanded = true }) { Text(state.profile.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SourceProfiles.ALL.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.label) },
                    onClick = {
                        onProfileChange(profile)
                        expanded = false
                    },
                )
            }
        }
    }
}
