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
import com.duzui.sharetoobsi.domain.SourceProfile
import com.duzui.sharetoobsi.domain.SourceProfiles
import com.duzui.sharetoobsi.domain.WriteMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    state: ShareUiState,
    message: String?,
    onProfileChange: (SourceProfile) -> Unit,
    onBookTitleChange: (String) -> Unit,
    onBookAuthorChange: (String) -> Unit,
    onBookYearChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onModeChange: (WriteMode) -> Unit,
    onSelectTarget: (Long?) -> Unit,
    onPayloadEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
    onCreateSkeleton: () -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
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

            TargetSection(state, onSelectTarget)
            PayloadField(state, onPayloadEdit, onRegenerate)
            BookFields(
                state = state,
                onTitle = onBookTitleChange,
                onAuthor = onBookAuthorChange,
                onYear = onBookYearChange,
                onTags = onTagsChange,
                onCreateSkeleton = onCreateSkeleton,
            )
            SourcePicker(state, onProfileChange)
            ModePicker(state, onModeChange)
        }
    }
}

@Composable
private fun TargetSection(state: ShareUiState, onSelectTarget: (Long?) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedTargetId == null,
                        onClick = { onSelectTarget(null) },
                        label = {
                            Text(
                                if (state.targetsBook) "当前书目" else "收件箱",
                            )
                        },
                    )
                    state.savedTargets.forEach { target ->
                        FilterChip(
                            selected = state.selectedTargetId == target.id,
                            onClick = { onSelectTarget(target.id) },
                            label = { Text(target.name) },
                        )
                    }
                }
            }

            Text(
                state.targetPath,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when {
                    state.settings.mode == WriteMode.OFFICIAL ->
                        "追加到文件末尾 —— 官方 URI 无法指定小节"
                    state.effectiveHeading != null -> "追加到小节：${state.effectiveHeading}"
                    else -> "追加到文件末尾"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BookFields(
    state: ShareUiState,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onYear: (String) -> Unit,
    onTags: (String) -> Unit,
    onCreateSkeleton: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.hintApplied) {
            Text(
                "书名和作者是从分享内容里认出来的，可以直接改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedTextField(
            value = state.bookTitle,
            onValueChange = onTitle,
            label = { Text("书名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.bookAuthor,
                onValueChange = onAuthor,
                label = { Text("作者") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.bookYear,
                onValueChange = onYear,
                label = { Text("年份") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = state.settings.tags,
            onValueChange = onTags,
            label = { Text("标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.targetsBook && !state.bookSeeded) {
            // A heading that does not exist makes Obsidian write nothing and report
            // nothing, so a brand new book needs its skeleton created first.
            Text(
                "Obsidian 找不到「${state.effectiveHeading ?: "目标小节"}」时会静默不写。" +
                    "这是本新书的话，先把骨架建起来。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onCreateSkeleton, modifier = Modifier.fillMaxWidth()) {
                Text("新建书目骨架（已有笔记请勿点）")
            }
        }
    }
}

@Composable
private fun PayloadField(
    state: ShareUiState,
    onEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "将写入的内容",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
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
        Text("原文 ${state.raw.length} 字", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SourcePicker(state: ShareUiState, onProfileChange: (SourceProfile) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "来源：${state.sourcePackage ?: "未知"}",
            style = MaterialTheme.typography.bodySmall,
        )
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
