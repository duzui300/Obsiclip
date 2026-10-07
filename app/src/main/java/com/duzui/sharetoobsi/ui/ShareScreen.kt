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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.ShareUiState
import com.duzui.sharetoobsi.data.TargetEntity
import com.duzui.sharetoobsi.domain.SourceProfile
import com.duzui.sharetoobsi.domain.SourceProfiles
import com.duzui.sharetoobsi.domain.WriteMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    state: ShareUiState,
    message: String?,
    onProfileChange: (SourceProfile) -> Unit,
    onModeChange: (WriteMode) -> Unit,
    onSelectTarget: (Long?) -> Unit,
    onAddTarget: (String, String, String, String, String, Boolean) -> Unit,
    onCreateSkeleton: (TargetEntity) -> Unit,
    onDerivePath: (String) -> String,
    onReadClipboard: () -> Unit,
    onPayloadEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var addingTarget by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    if (addingTarget) {
        AddTargetDialog(
            defaultHeading = state.settings.heading,
            derivePath = onDerivePath,
            onDismiss = { addingTarget = false },
            onSave = { name, author, year, path, heading, skeleton ->
                onAddTarget(name, author, year, path, heading, skeleton)
                addingTarget = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("摘录入 Obsidian") },
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
                Text("写入 Obsidian", style = MaterialTheme.typography.titleMedium)
            }

            TargetSection(
                state = state,
                onSelectTarget = onSelectTarget,
                onAddTarget = { addingTarget = true },
            )

            state.chosenTarget?.takeIf { !it.seeded }?.let { target ->
                // A heading that does not exist makes Obsidian write nothing and report
                // nothing, so a brand new note needs its skeleton creating first.
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Obsidian 找不到「${state.effectiveHeading ?: state.settings.heading}」" +
                                "时会静默不写。这是本新书的话，先建骨架。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { onCreateSkeleton(target) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("新建「${target.name}」的笔记骨架（已有笔记请勿点）")
                        }
                    }
                }
            }

            if (state.raw.isBlank()) {
                EmptyState(state.origin, onReadClipboard)
            } else {
                PayloadField(state, onPayloadEdit, onRegenerate, onReadClipboard)
            }

            SourcePicker(state, onProfileChange)
            ModePicker(state, onModeChange)
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
                if (origin.isBlank()) "还没有内容" else "$origin —— 但内容为空",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "从别的 App 分享过来，或者在那里选中文字后「复制」，再读进来。\n" +
                    "分享面板有长度限制的 App（比如 Kindle），走复制这条路更稳，也更干净。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onReadClipboard, modifier = Modifier.fillMaxWidth()) {
                Text("从剪贴板读取")
            }
        }
    }
}

@Composable
private fun TargetSection(
    state: ShareUiState,
    onSelectTarget: (Long?) -> Unit,
    onAddTarget: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedTargetId == null,
                    onClick = { onSelectTarget(null) },
                    label = { Text("收件箱") },
                )
                state.savedTargets.forEach { target ->
                    FilterChip(
                        selected = state.selectedTargetId == target.id,
                        onClick = { onSelectTarget(target.id) },
                        label = { Text(target.name) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onAddTarget,
                    label = { Text("＋ 书目") },
                )
            }

            Text(state.targetPath, style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    state.settings.mode == WriteMode.OFFICIAL ->
                        "追加到文件末尾 —— 官方 URI 无法指定小节"
                    state.effectiveHeading != null -> "追加到小节：${state.effectiveHeading}"
                    else -> "追加到文件末尾（没选书目的内容先进收件箱）"
                },
                style = MaterialTheme.typography.bodySmall,
            )
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
                "将写入的内容",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReadClipboard) { Text("重读剪贴板") }
            if (state.payloadOverride != null) {
                TextButton(onClick = onRegenerate) { Text("重新生成") }
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
            "${state.origin} · 原文 ${state.raw.length} 字",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SourcePicker(state: ShareUiState, onProfileChange: (SourceProfile) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("清洗规则", modifier = Modifier.weight(1f))
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

@Composable
private fun ModePicker(state: ShareUiState, onModeChange: (WriteMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("写入方式", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.settings.mode == WriteMode.ADVANCED,
                onClick = { onModeChange(WriteMode.ADVANCED) },
                label = { Text("Advanced URI") },
            )
            FilterChip(
                selected = state.settings.mode == WriteMode.OFFICIAL,
                onClick = { onModeChange(WriteMode.OFFICIAL) },
                label = { Text("官方 URI") },
            )
        }
    }
}
