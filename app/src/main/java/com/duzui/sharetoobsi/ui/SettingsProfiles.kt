package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.duzui.sharetoobsi.AppEntry
import com.duzui.sharetoobsi.data.ProfileEntity
import com.duzui.sharetoobsi.domain.SourceProfile

/** Rule sets the user wrote, with their own optional output template. */
@Composable
fun RulesSection(
    userProfiles: List<ProfileEntity>,
    placeholderTemplate: String,
    onSave: (Long?, String, String, String, String) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        ProfileEditorDialog(
            existing = editing,
            placeholderTemplate = placeholderTemplate,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { id, name, lineRules, inlineRules, template ->
                onSave(id, name, lineRules, inlineRules, template)
                creating = false
                editing = null
            },
        )
    }

    val lineRules = stringResource(R.string.profiles_line_rules)
    val ownTemplate = stringResource(R.string.profiles_own_template)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.profiles_own_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_new))
        }

        userProfiles.forEach { profile ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        buildString {
                            append(profile.lineRules.lines().count { it.isNotBlank() })
                                .append(lineRules)
                            val inline = profile.inlineRules.lines().count { it.isNotBlank() }
                            if (inline > 0) append(stringResource(R.string.profiles_inline_rules, inline))
                            if (profile.template.isNotBlank()) append(ownTemplate)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { editing = profile }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = { onDelete(profile) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

/** Pins a source app to a profile, overriding whatever package matching would guess. */
@Composable
fun AppProfileSection(
    installedApps: List<AppEntry>,
    mappings: Map<String, String>,
    availableProfiles: List<SourceProfile>,
    onSet: (String, String) -> Unit,
    onClear: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        AppPickerDialog(
            apps = installedApps,
            onPick = { app ->
                // Defaults to the built-in generic until the user chooses otherwise.
                onSet(app.packageName, availableProfiles.first().id)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.profiles_app_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profiles_add_app))
        }

        mappings.forEach { (packageName, profileId) ->
            val label = installedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(packageName, style = MaterialTheme.typography.bodySmall)
                }
                ProfileDropdown(
                    current = availableProfiles.firstOrNull { it.id == profileId },
                    options = availableProfiles,
                    onPick = { onSet(packageName, it.id) },
                )
                IconButton(onClick = { onClear(packageName) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }
    }
}

@Composable
private fun ProfileDropdown(
    current: SourceProfile?,
    options: List<SourceProfile>,
    onPick: (SourceProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) { Text(current?.label ?: stringResource(R.string.action_choose)) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { profile ->
            DropdownMenuItem(
                text = { Text(profile.label) },
                onClick = {
                    onPick(profile)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_choose_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.profiles_no_match), style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = { onPick(app) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
