package com.duzui.sharetoobsi.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.ShareUiState
import com.duzui.sharetoobsi.data.ProfileEntity
import com.duzui.sharetoobsi.data.TargetEntity
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Template
import com.duzui.sharetoobsi.domain.WriteMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: ShareUiState,
    onBack: () -> Unit,
    onVault: (String) -> Unit,
    onHeading: (String) -> Unit,
    onTemplate: (String) -> Unit,
    onPathTemplate: (String) -> Unit,
    onInboxPath: (String) -> Unit,
    onTags: (String) -> Unit,
    onMode: (WriteMode) -> Unit,
    onSilent: (Boolean) -> Unit,
    onAutoReadClipboard: (Boolean) -> Unit,
    onReturnToSource: (Boolean) -> Unit,
    onCleanup: (CleanupOptions) -> Unit,
    onDefaultProfileId: (String) -> Unit,
    onSaveProfile: (Long?, String, String, String, String) -> Unit,
    onDeleteProfile: (ProfileEntity) -> Unit,
    onSetAppMapping: (String, String) -> Unit,
    onClearAppMapping: (String) -> Unit,
    onSaveTarget: (String, String, String, String, String, Boolean) -> Unit,
    onDerivePath: (String) -> String,
    onDeleteTarget: (TargetEntity) -> Unit,
    onOpenHistory: () -> Unit,
    onRetryOutbox: () -> Unit,
) {
    val settings = state.settings
    var addingTarget by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }

    if (addingTarget) {
        AddTargetDialog(
            defaultHeading = settings.heading,
            derivePath = onDerivePath,
            onDismiss = { addingTarget = false },
            onSave = { name, author, year, path, heading, skeleton ->
                onSaveTarget(name, author, year, path, heading, skeleton)
                addingTarget = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("写入目标")
            Field("Vault 名称", settings.vault, onVault)
            Field("小节标题", settings.heading, onHeading, "留空则追加到文件末尾")
            Field("书目路径模板", settings.pathTemplate, onPathTemplate, "可用 {title} {author} {year} {date}")
            Field("收件箱笔记", settings.inboxPath, onInboxPath, "没选书目时写到这里")

            Section("输出格式")
            Field(
                "模板",
                settings.template,
                onTemplate,
                "占位符：" + Template.placeholders.joinToString(" ") { "{$it}" },
                singleLine = false,
            )
            Field("标签", settings.tags, onTags)

            Section("写入方式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onMode(WriteMode.ADVANCED) }) {
                    Text(if (settings.mode == WriteMode.ADVANCED) "● Advanced URI" else "○ Advanced URI")
                }
                OutlinedButton(onClick = { onMode(WriteMode.OFFICIAL) }) {
                    Text(if (settings.mode == WriteMode.OFFICIAL) "● 官方 URI" else "○ 官方 URI")
                }
            }
            Text(
                if (settings.mode == WriteMode.ADVANCED) {
                    "能定位到指定小节，需要装 Advanced URI 插件。"
                } else {
                    "不需要插件，但只能追加到文件末尾，无法指定小节。"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Toggle("静默写入（不跳到 Obsidian）", settings.silent, onSilent)
            Toggle("打开 App 时自动读取剪贴板", settings.autoReadClipboard, onAutoReadClipboard)
            Text(
                "只在从桌面或磁贴打开时生效；由分享进入时不受影响。快捷设置磁贴始终读取剪贴板。",
                style = MaterialTheme.typography.bodySmall,
            )
            Toggle("写入后返回来源 App", settings.returnToSource, onReturnToSource)

            Section("清洗流水线")
            val cleanup = settings.cleanup
            Toggle("归一化换行与不可见字符", cleanup.normalize) { onCleanup(cleanup.copy(normalize = it)) }
            Toggle("按来源规则去杂质", cleanup.stripBoilerplate) { onCleanup(cleanup.copy(stripBoilerplate = it)) }
            Toggle("删除只有链接的行", cleanup.stripLoneUrlLines) { onCleanup(cleanup.copy(stripLoneUrlLines = it)) }
            Toggle("删除「说明文字＋链接」的尾行", cleanup.dropLinkFooterLines) {
                onCleanup(cleanup.copy(dropLinkFooterLines = it))
            }
            Toggle("合并被硬换行截断的句子", cleanup.unwrapLines) { onCleanup(cleanup.copy(unwrapLines = it)) }
            Toggle("压缩连续空行", cleanup.collapseBlankLines) { onCleanup(cleanup.copy(collapseBlankLines = it)) }
            Toggle("包成引用块", cleanup.wrapQuote) { onCleanup(cleanup.copy(wrapQuote = it)) }
            Toggle("包成 ==高亮==（在引用块内）", cleanup.wrapHighlight) { onCleanup(cleanup.copy(wrapHighlight = it)) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("默认规则", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { profileMenu = true }) {
                    Text(state.availableProfiles.firstOrNull { it.id == settings.defaultProfileId }?.label ?: "通用")
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
            Text("认不出来源的分享用这套规则。", style = MaterialTheme.typography.bodySmall)

            Section("自定义规则")
            RulesSection(
                userProfiles = state.userProfiles,
                placeholderTemplate = settings.template,
                onSave = onSaveProfile,
                onDelete = onDeleteProfile,
            )

            Section("按 App 指定规则")
            AppProfileSection(
                installedApps = state.installedApps,
                mappings = state.appMappings,
                availableProfiles = state.availableProfiles,
                onSet = onSetAppMapping,
                onClear = onClearAppMapping,
            )

            Section("预设目标")
            Text(
                "把正在读的几本书加进来，分享时点一下芯片就能选，不用每次打字。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { addingTarget = true }) { Text("添加目标") }
            state.savedTargets.forEach { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(target.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append(target.path)
                                if (target.heading.isBlank()) {
                                    append("（文件末尾）")
                                } else {
                                    append(" › ").append(target.heading)
                                }
                                val by = listOf(target.author, target.year).filter { it.isNotBlank() }
                                if (by.isNotEmpty()) append("　— ").append(by.joinToString(" "))
                                if (!target.seeded) append("　· 未建骨架")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { onDeleteTarget(target) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
                HorizontalDivider()
            }

            Section("记录")
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Text("发送历史（${state.history.size} 条）")
            }
            Text(
                "发送失败的条目会进待发队列。为避免打扰，只在你打开本 App 时重发 —— " +
                    "Android 不允许后台启动 Obsidian。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRetryOutbox) { Text("立即重发待发队列") }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium)
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
