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
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.BookDraft
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

    var editingTarget by remember { mutableStateOf<TargetEntity?>(null) }
    var addingTarget by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<BookEntity?>(null) }
    var addingBook by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var defaultTargetMenu by remember { mutableStateOf(false) }

    // Which sections are shut. Kept as a list of titles so it survives a rotation.
    // Everything starts folded. The screen grew past the point where an open list
    // of nine sections reads as anything.
    var collapsed by rememberSaveable { mutableStateOf(ArrayList(SECTION_TITLES)) }
    fun toggle(title: String) {
        collapsed = if (title in collapsed) {
            ArrayList(collapsed.filterNot { it == title })
        } else {
            ArrayList(collapsed + title)
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
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Text(
                        if (collapsed.isEmpty()) "全部展开" else "点标题可折叠",
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
            Section("写入目标", collapsed, ::toggle) {
                Field("Vault 名称", settings.vault, onVault)
                Field("默认小节标题", settings.heading, onHeading, "添加目的地时的默认值")
                Field(
                    "新目的地的默认路径",
                    settings.pathTemplate,
                    onPathTemplate,
                    "可用 {title} {author} {year} {date}",
                )
            }

            Section("目的地", collapsed, ::toggle) {
                Text(
                    "写到哪、写到哪个小节、用哪种格式。路径可以用 {title} {author} {year}，" +
                        "由选中的书填写 —— 所以一条「书目笔记」就够所有书用。",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("默认收件箱", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { defaultTargetMenu = true }) {
                        Text(
                            state.savedTargets.firstOrNull { it.id == settings.defaultTargetId }?.name
                                ?: "第一个目标"
                        )
                    }
                    DropdownMenu(
                        expanded = defaultTargetMenu,
                        onDismissRequest = { defaultTargetMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("第一个目标") },
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

                Button(onClick = { addingTarget = true }) { Text("添加目的地") }
                if (state.savedTargets.size > 1) {
                    Text("长按拖动排序，和写入界面的顺序一致。", style = MaterialTheme.typography.bodySmall)
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
                                        append("（文件末尾）")
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
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { onDeleteTarget(target) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                    HorizontalDivider()
                }
            }

            Section("书籍", collapsed, ::toggle) {
                Text(
                    "在读什么。只提供书名、作者、年份这些信息，不决定写到哪。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { addingBook = true }) { Text("添加书籍") }
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
                                ).joinToString(" · ").ifBlank { "（没有作者和年份）" },
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

            Section("输出格式", collapsed, ::toggle) {
                Field(
                    "默认格式模板",
                    settings.template,
                    onTemplate,
                    "占位符：" + Template.placeholders.joinToString(" ") { "{$it}" },
                    singleLine = false,
                )
                Field("标签", settings.tags, onTags)
                FormatsSection(
                    formats = state.formats,
                    onSave = onSaveFormat,
                    onDelete = onDeleteFormat,
                )
            }

            Section("写入方式", collapsed, ::toggle) {
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
                Toggle("写入后返回来源 App", settings.returnToSource, onReturnToSource)
                Text(
                    "不勾选时，Obsidian 会打开刚写入的那篇笔记并停在那里。" +
                        "勾上就立刻回到你分享的地方。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Toggle("打开 App 时自动读取剪贴板", settings.autoReadClipboard, onAutoReadClipboard)
                Text(
                    "从桌面或磁贴打开时覆盖当前内容；由分享进入时不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("清洗流水线", collapsed, ::toggle) {
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
                        Text(
                            state.availableProfiles.firstOrNull { it.id == settings.defaultProfileId }?.label
                                ?: "通用"
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
                Text("认不出来源的分享用这套规则。", style = MaterialTheme.typography.bodySmall)
            }

            Section("自定义规则", collapsed, ::toggle) {
                RulesSection(
                    userProfiles = state.userProfiles,
                    placeholderTemplate = settings.template,
                    onSave = onSaveProfile,
                    onDelete = onDeleteProfile,
                )
            }

            Section("按 App 指定规则", collapsed, ::toggle) {
                AppProfileSection(
                    installedApps = state.installedApps,
                    mappings = state.appMappings,
                    availableProfiles = state.availableProfiles,
                    onSet = onSetAppMapping,
                    onClear = onClearAppMapping,
                )
            }

            Section("记录", collapsed, ::toggle) {
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
            Section("实验功能", collapsed, ::toggle) {
                Text(
                    "为了绕开 Kindle 的分享与复制限制：它在阅读界面禁止选择长段落，所以整段分享和" +
                        "复制都会失败；而「注解」页把每条划线当普通文字放进无障碍树，直接读它就行 —— " +
                        "不需要选择，也不需要剪贴板。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "实验功能：判据是按屏幕结构写的，Kindle 改版后可能失效，所以抓到的结果一律先" +
                        "给你过一遍再写。",
                    style = MaterialTheme.typography.bodySmall,
                )
                // A finished run has to be reachable without the notification: missing it
                // meant the highlights sat in storage with nothing pointing at them.
                if (state.pendingImportCount > 0) {
                    Text(
                        "上次抓到的还在等着检查。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(onClick = onOpenPendingImport) {
                        Text("检查上次抓到的 ${state.pendingImportCount} 条")
                    }
                }
                when (collectorStatus) {
                    CollectorStatus.Running -> {
                        Text(
                            "已就绪。在 Kindle 里打开这本书的注解页，点下面的按钮，它会自己读完。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onArmKindleImport) { Text("开始抓取") }
                    }

                    // Cost the most time of anything here: the switch looks on while
                    // nothing is listening, so the only fix is to toggle it off and back on.
                    CollectorStatus.EnabledNotRunning -> {
                        Text(
                            "系统里显示已开启，但服务其实没在运行（在小米系统上见过）。" +
                                "把那个开关关掉再打开一次就好。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Text("去无障碍设置里重开一次")
                        }
                    }

                    CollectorStatus.NotEnabled -> {
                        Text(
                            "服务还没开。路径：系统设置 → 无障碍 → 「已下载的服务」" +
                                "（部分机型叫「已安装的服务」）→ 找到「从 Kindle 的注解页收集划线」" +
                                "→ 打开，并在弹窗里点「允许」。" +
                                "注意不是页面顶部的「无障碍快捷方式」，那个是给快捷键用的。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Text("去开启无障碍权限")
                        }
                    }
                }
                Toggle("抓取后直接写入，不过一遍", settings.autoWriteImports, onAutoWriteImports)
                Text(
                    "默认关。Kindle 会把长划线折叠，折叠后的样子和短划线一模一样 —— " +
                        "开了就直接写，被折叠过的和被写过的仍然会留着让你确认。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

        }
    }
}

/** Every section, so all of them can start folded. */
private val SECTION_TITLES = listOf(
    "写入目标",
    "目的地",
    "书籍",
    "输出格式",
    "写入方式",
    "清洗流水线",
    "自定义规则",
    "按 App 指定规则",
    "记录",
    "实验功能",
)

/** A section whose body hides behind its title, so a long screen stays scannable. */
@Composable
private fun Section(
    title: String,
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    HorizontalDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(title) }
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (title in collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (title in collapsed) "展开" else "收起",
        )
    }
    if (title !in collapsed) {
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
