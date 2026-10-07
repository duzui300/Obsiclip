package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.BookDraft
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.ShareUiState
import com.duzui.sharetoobsi.TargetDraft
import com.duzui.sharetoobsi.data.BookEntity
import com.duzui.sharetoobsi.data.FormatEntity
import com.duzui.sharetoobsi.data.ProfileEntity
import com.duzui.sharetoobsi.data.TargetEntity
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Template
import com.duzui.sharetoobsi.domain.WriteMode
import com.duzui.sharetoobsi.send.CollectorStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: ShareUiState,
    onBack: () -> Unit,
    onVault: (String) -> Unit,
    onHeading: (String) -> Unit,
    onTemplate: (String) -> Unit,
    onPathTemplate: (String) -> Unit,
    onTags: (String) -> Unit,
    onMode: (WriteMode) -> Unit,
    onAutoReadClipboard: (Boolean) -> Unit,
    onAutoWriteImports: (Boolean) -> Unit,
    onReturnToSource: (Boolean) -> Unit,
    onCleanup: (CleanupOptions) -> Unit,
    onDefaultProfileId: (String) -> Unit,
    onSaveProfile: (Long?, String, String, String, String) -> Unit,
    onDeleteProfile: (ProfileEntity) -> Unit,
    onSetAppMapping: (String, String) -> Unit,
    onClearAppMapping: (String) -> Unit,
    onSaveTarget: (TargetDraft) -> Unit,
    onMoveTarget: (Int, Int) -> Unit,
    onDeleteTarget: (TargetEntity) -> Unit,
    onDefaultTargetId: (Long?) -> Unit,
    onSaveFormat: (Long?, String, String) -> Unit,
    onDeleteFormat: (FormatEntity) -> Unit,
    onSaveBook: (BookDraft) -> Unit,
    onMoveBook: (Int, Int) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onDeriveSkeletonPath: (String) -> String,
    onOpenHistory: () -> Unit,
    onRetryOutbox: () -> Unit,
    collectorStatus: CollectorStatus,
    onArmKindleImport: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenPendingImport: () -> Unit,
) {
    val settings = state.settings
    // Fetched up front: a lambda that is not itself composable cannot call stringResource.
    val endOfFile = stringResource(R.string.settings_end_of_file)
    val noAuthorYear = stringResource(R.string.settings_no_author_year)

    var editingTarget by remember { mutableStateOf<TargetEntity?>(null) }
    var addingTarget by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<BookEntity?>(null) }
    var addingBook by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var defaultTargetMenu by remember { mutableStateOf(false) }

    // Which sections are shut. Kept as a list of titles so it survives a rotation.
    // Everything starts folded. The screen grew past the point where an open list
    // of nine sections reads as anything.
    var collapsed by rememberSaveable {
        mutableStateOf(ArrayList(SettingsSection.entries.map { it.name }))
    }
    fun toggle(section: SettingsSection) {
        val key = section.name
        collapsed = if (key in collapsed) {
            ArrayList(collapsed.filterNot { it == key })
        } else {
            ArrayList(collapsed + key)
        }
    }

    if (addingTarget || editingTarget != null) {
        AddTargetDialog(
            existing = editingTarget,
            defaultPath = settings.pathTemplate,
            defaultHeading = settings.heading,
            formats = state.formats,
            onDismiss = {
                addingTarget = false
                editingTarget = null
            },
            onSave = { draft ->
                onSaveTarget(draft)
                addingTarget = false
                editingTarget = null
            },
        )
    }
    if (addingBook || editingBook != null) {
        AddBookDialog(
            existing = editingBook,
            deriveSkeletonPath = onDeriveSkeletonPath,
            onDismiss = {
                addingBook = false
                editingBook = null
            },
            onSave = { draft ->
                onSaveBook(draft)
                addingBook = false
                editingBook = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Text(
                        if (collapsed.isEmpty()) stringResource(R.string.settings_expand_all)
                        else stringResource(R.string.settings_collapse_hint),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(SettingsSection.WriteTarget, collapsed, ::toggle) {
                Field(stringResource(R.string.settings_vault_name), settings.vault, onVault)
                Field(
                    stringResource(R.string.settings_default_heading),
                    settings.heading,
                    onHeading,
                    stringResource(R.string.settings_default_heading_hint),
                )
                Field(
                    stringResource(R.string.settings_default_path),
                    settings.pathTemplate,
                    onPathTemplate,
                    stringResource(R.string.settings_path_placeholders),
                )
            }

            Section(SettingsSection.Destinations, collapsed, ::toggle) {
                Text(
                    stringResource(R.string.settings_destinations_hint),
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_default_inbox), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { defaultTargetMenu = true }) {
                        Text(
                            state.savedTargets.firstOrNull { it.id == settings.defaultTargetId }?.name
                                ?: stringResource(R.string.settings_first_target)
                        )
                    }
                    DropdownMenu(
                        expanded = defaultTargetMenu,
                        onDismissRequest = { defaultTargetMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_first_target)) },
                            onClick = {
                                onDefaultTargetId(null)
                                defaultTargetMenu = false
                            },
                        )
                        state.savedTargets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.name) },
                                onClick = {
                                    onDefaultTargetId(target.id)
                                    defaultTargetMenu = false
                                },
                            )
                        }
                    }
                }

                Button(onClick = { addingTarget = true }) {
                    Text(stringResource(R.string.settings_add_destination))
                }
                if (state.savedTargets.size > 1) {
                    Text(
                    stringResource(R.string.settings_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                }

                val targetReorder = rememberReorderState(state.savedTargets.size)
                state.savedTargets.forEachIndexed { index, target ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.reorderDrag(
                            key = target.id,
                            index = index,
                            state = targetReorder,
                            horizontal = false,
                            onMove = onMoveTarget,
                        ),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(target.path)
                                    if (target.heading.isBlank()) {
                                        append(endOfFile)
                                    } else {
                                        append(" › ").append(target.heading)
                                    }
                                    state.formats.firstOrNull { it.id == target.formatId }?.let {
                                        append("　· ").append(it.name)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { editingTarget = target }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { onDeleteTarget(target) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    HorizontalDivider()
                }
            }

            Section(SettingsSection.Books, collapsed, ::toggle) {
                Text(
                    stringResource(R.string.settings_books_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { addingBook = true }) {
                    Text(stringResource(R.string.settings_add_book))
                }
                if (state.savedBooks.size > 1) {
                    Text("长按拖动排序，和写入界面的顺序一致。", style = MaterialTheme.typography.bodySmall)
                }

                val bookReorder = rememberReorderState(state.savedBooks.size)
                state.savedBooks.forEachIndexed { index, book ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.reorderDrag(
                            key = book.id,
                            index = index,
                            state = bookReorder,
                            horizontal = false,
                            onMove = onMoveBook,
                        ),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    book.author.takeIf { it.isNotBlank() },
                                    book.year.takeIf { it.isNotBlank() },
                                ).joinToString(" · ").ifBlank { noAuthorYear },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { editingBook = book }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { onDeleteBook(book) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                    HorizontalDivider()
                }
            }

            Section(SettingsSection.Formats, collapsed, ::toggle) {
                Field(
                    stringResource(R.string.settings_default_format),
                    settings.template,
                    onTemplate,
                    stringResource(R.string.settings_placeholders_prefix) + Template.placeholders.joinToString(" ") { "{$it}" },
                    singleLine = false,
                )
                Field(stringResource(R.string.settings_tags), settings.tags, onTags)
                FormatsSection(
                    formats = state.formats,
                    onSave = onSaveFormat,
                    onDelete = onDeleteFormat,
                )
            }

            Section(SettingsSection.WriteMode, collapsed, ::toggle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMode(WriteMode.ADVANCED) }) {
                        Text(if (settings.mode == WriteMode.ADVANCED) "● Advanced URI" else "○ Advanced URI")
                    }
                    OutlinedButton(onClick = { onMode(WriteMode.OFFICIAL) }) {
                        Text(
                    stringResource(
                        if (settings.mode == WriteMode.OFFICIAL) R.string.settings_mode_official_on
                        else R.string.settings_mode_official_off
                    )
                )
                    }
                }
                Text(
                    if (settings.mode == WriteMode.ADVANCED) {
                        stringResource(R.string.settings_mode_advanced_hint)
                    } else {
                        stringResource(R.string.settings_mode_official_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Toggle(
                    stringResource(R.string.settings_return_to_source),
                    settings.returnToSource,
                    onReturnToSource,
                )
                Text(
                    stringResource(R.string.settings_return_to_source_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Toggle(
                    stringResource(R.string.settings_read_clipboard_on_open),
                    settings.autoReadClipboard,
                    onAutoReadClipboard,
                )
                Text(
                    stringResource(R.string.settings_read_clipboard_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section(SettingsSection.Cleanup, collapsed, ::toggle) {
                val cleanup = settings.cleanup
                Toggle(stringResource(R.string.cleanup_normalise), cleanup.normalize) { onCleanup(cleanup.copy(normalize = it)) }
                Toggle(stringResource(R.string.cleanup_strip), cleanup.stripBoilerplate) { onCleanup(cleanup.copy(stripBoilerplate = it)) }
                Toggle(stringResource(R.string.cleanup_lone_urls), cleanup.stripLoneUrlLines) { onCleanup(cleanup.copy(stripLoneUrlLines = it)) }
                Toggle(stringResource(R.string.cleanup_link_footer), cleanup.dropLinkFooterLines) {
                    onCleanup(cleanup.copy(dropLinkFooterLines = it))
                }
                Toggle(stringResource(R.string.cleanup_unwrap), cleanup.unwrapLines) { onCleanup(cleanup.copy(unwrapLines = it)) }
                Toggle(stringResource(R.string.cleanup_blank_runs), cleanup.collapseBlankLines) { onCleanup(cleanup.copy(collapseBlankLines = it)) }
                Toggle(stringResource(R.string.cleanup_quote), cleanup.wrapQuote) { onCleanup(cleanup.copy(wrapQuote = it)) }
                Toggle(stringResource(R.string.cleanup_highlight), cleanup.wrapHighlight) { onCleanup(cleanup.copy(wrapHighlight = it)) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_default_profile), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { profileMenu = true }) {
                        Text(
                            state.availableProfiles.firstOrNull { it.id == settings.defaultProfileId }?.label
                                ?: stringResource(R.string.profile_generic)
                        )
                    }
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        state.availableProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.label) },
                                onClick = {
                                    onDefaultProfileId(profile.id)
                                    profileMenu = false
                                },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_default_profile_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section(SettingsSection.CustomRules, collapsed, ::toggle) {
                RulesSection(
                    userProfiles = state.userProfiles,
                    placeholderTemplate = settings.template,
                    onSave = onSaveProfile,
                    onDelete = onDeleteProfile,
                )
            }

            Section(SettingsSection.AppRules, collapsed, ::toggle) {
                AppProfileSection(
                    installedApps = state.installedApps,
                    mappings = state.appMappings,
                    availableProfiles = state.availableProfiles,
                    onSet = onSetAppMapping,
                    onClear = onClearAppMapping,
                )
            }

            Section(SettingsSection.Records, collapsed, ::toggle) {
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_history_button, state.history.size))
                }
                Text(
                stringResource(R.string.settings_outbox_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onRetryOutbox) { Text(stringResource(R.string.settings_retry_now)) }
            }
            Section(SettingsSection.Experimental, collapsed, ::toggle) {
                Text(
                    stringResource(R.string.settings_experimental_intro),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.settings_experimental_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                // A finished run has to be reachable without the notification: missing it
                // meant the highlights sat in storage with nothing pointing at them.
                if (state.pendingImportCount > 0) {
                    Text(
                        stringResource(R.string.settings_pending_import),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(onClick = onOpenPendingImport) {
                        Text(stringResource(R.string.settings_open_pending, state.pendingImportCount))
                    }
                }
                when (collectorStatus) {
                    CollectorStatus.Running -> {
                        Text(
                            stringResource(R.string.settings_collector_ready),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onArmKindleImport) {
                        Text(stringResource(R.string.settings_start_collect))
                    }
                    }

                    // Cost the most time of anything here: the switch looks on while
                    // nothing is listening, so the only fix is to toggle it off and back on.
                    CollectorStatus.EnabledNotRunning -> {
                        Text(
                        stringResource(R.string.settings_collector_not_running),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Text(stringResource(R.string.settings_open_accessibility))
                        }
                    }

                    CollectorStatus.NotEnabled -> {
                        Text(
                        stringResource(R.string.settings_collector_off),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Text(stringResource(R.string.settings_enable_accessibility))
                        }
                    }
                }
                Toggle(
                    stringResource(R.string.settings_auto_write_imports),
                    settings.autoWriteImports,
                    onAutoWriteImports,
                )
                Text(
                    stringResource(R.string.settings_auto_write_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

        }
    }
}

/**
 * A section, so all of them can start folded.
 *
 * The fold state is keyed on the enum, never on the label: keying it on display text would
 * silently reset every fold the moment the device language changed.
 */
private enum class SettingsSection(val labelRes: Int) {
    WriteTarget(R.string.section_write_target),
    Destinations(R.string.section_destinations),
    Books(R.string.section_books),
    Formats(R.string.section_formats),
    WriteMode(R.string.section_write_mode),
    Cleanup(R.string.section_cleanup),
    CustomRules(R.string.section_custom_rules),
    AppRules(R.string.section_app_rules),
    Records(R.string.section_records),
    Experimental(R.string.section_experimental),
}

/** A section whose body hides behind its title, so a long screen stays scannable. */
@Composable
private fun Section(
    section: SettingsSection,
    collapsed: List<String>,
    onToggle: (SettingsSection) -> Unit,
    content: @Composable () -> Unit,
) {
    val key = section.name
    val folded = key in collapsed
    HorizontalDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(section) }
            .padding(vertical = 8.dp),
    ) {
        Text(
            stringResource(section.labelRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (folded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(
                if (folded) R.string.action_expand else R.string.action_collapse
            ),
        )
    }
    if (!folded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    supporting: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
