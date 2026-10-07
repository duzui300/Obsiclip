package com.duzui.sharetoobsi.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
    onPayloadEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSend: () -> Unit,
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
        topBar = { TopAppBar(title = { Text("摘录入 Obsidian") }) },
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

            TargetCard(state)
            PayloadField(state, onPayloadEdit, onRegenerate)
            BookFields(state, onBookTitleChange, onBookAuthorChange, onBookYearChange, onTagsChange)
            SourcePicker(state, onProfileChange)
            ModePicker(state, onModeChange)
        }
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
private fun BookFields(
    state: ShareUiState,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onYear: (String) -> Unit,
    onTags: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            value = state.tags,
            onValueChange = onTags,
            label = { Text("标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TargetCard(state: ShareUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (state.targetsBook) "目标笔记" else "目标笔记（没填书名，落到收件箱）",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(state.targetPath, style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    state.mode == WriteMode.OFFICIAL -> "追加到文件末尾 —— 官方 URI 无法指定小节"
                    state.effectiveHeading != null -> "追加到小节：${state.effectiveHeading}"
                    else -> "追加到文件末尾"
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
private fun ModePicker(state: ShareUiState, onModeChange: (WriteMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("写入方式", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == WriteMode.ADVANCED,
                onClick = { onModeChange(WriteMode.ADVANCED) },
                label = { Text("Advanced URI") },
            )
            FilterChip(
                selected = state.mode == WriteMode.OFFICIAL,
                onClick = { onModeChange(WriteMode.OFFICIAL) },
                label = { Text("官方 URI") },
            )
        }
    }
}
