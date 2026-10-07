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
import com.duzui.sharetoobsi.data.AppSettings
import com.duzui.sharetoobsi.data.TargetEntity
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Template
import com.duzui.sharetoobsi.domain.WriteMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    targets: List<TargetEntity>,
    onBack: () -> Unit,
    onVault: (String) -> Unit,
    onHeading: (String) -> Unit,
    onTemplate: (String) -> Unit,
    onPathTemplate: (String) -> Unit,
    onInboxPath: (String) -> Unit,
    onTags: (String) -> Unit,
    onMode: (WriteMode) -> Unit,
    onSilent: (Boolean) -> Unit,
    onReturnToSource: (Boolean) -> Unit,
    onCleanup: (CleanupOptions) -> Unit,
    onSaveTarget: (String, String, String) -> Unit,
    onDeleteTarget: (TargetEntity) -> Unit,
    onRetryOutbox: () -> Unit,
) {
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
            Field("收件箱笔记", settings.inboxPath, onInboxPath, "没填书名时写到这里")

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

            Section("预设目标")
            TargetForm(onSaveTarget)
            targets.forEach { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(target.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            target.path + if (target.heading.isBlank()) "（文件末尾）" else " › ${target.heading}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { onDeleteTarget(target) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
                HorizontalDivider()
            }

            Section("待发队列")
            Text(
                "发送失败的条目会存在这里。为避免打扰，只在你打开本 App 时重发 —— " +
                    "Android 不允许后台启动 Obsidian。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRetryOutbox) { Text("立即重发") }
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
        modifier = Modifier
            .fillMaxWidth()
            .let { if (singleLine) it else it.padding(top = 0.dp) },
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TargetForm(onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var heading by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("名称", name, { name = it })
        Field("vault 相对路径", path, { path = it })
        Field("小节标题（可留空）", heading, { heading = it })
        Button(
            onClick = {
                onSave(name, path, heading)
                name = ""
                path = ""
                heading = ""
            },
        ) { Text("添加目标") }
    }
}
