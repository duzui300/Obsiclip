package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.TargetDraft
import com.duzui.sharetoobsi.data.FormatEntity
import com.duzui.sharetoobsi.data.TargetEntity

/**
 * Adds or edits a target — a book, the inbox, or any other destination.
 *
 * Typing the book once here is what replaces guessing it out of the share text: a wrong
 * guess files a highlight into a brand new note, and the note it should have gone to is
 * usually sitting right there already.
 */
@Composable
fun AddTargetDialog(
    existing: TargetEntity?,
    defaultHeading: String,
    formats: List<FormatEntity>,
    derivePath: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (TargetDraft) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var author by remember { mutableStateOf(existing?.author.orEmpty()) }
    var year by remember { mutableStateOf(existing?.year.orEmpty()) }
    var heading by remember { mutableStateOf(existing?.heading ?: defaultHeading) }
    var formatId by remember { mutableStateOf(existing?.formatId) }
    var createSkeleton by remember { mutableStateOf(false) }

    // The path follows the name until the user overrules it; an existing target is
    // overruling it already.
    var pathEdited by remember { mutableStateOf(existing != null) }
    var pathOverride by remember { mutableStateOf(existing?.path.orEmpty()) }
    val path = if (pathEdited) pathOverride else derivePath(name)

    var formatMenu by remember { mutableStateOf(false) }
    val formatLabel = formats.firstOrNull { it.id == formatId }?.name ?: "默认格式"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加目标" else "编辑目标") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("书名 / 名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("年份") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = {
                        pathEdited = true
                        pathOverride = it
                    },
                    label = { Text("笔记路径") },
                    supportingText = { Text("按设置里的路径模板生成，可改") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = heading,
                    onValueChange = { heading = it },
                    label = { Text("小节标题") },
                    supportingText = { Text("留空则追加到文件末尾") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("输出格式", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { formatMenu = true }) { Text(formatLabel) }
                    DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("默认格式") },
                            onClick = {
                                formatId = null
                                formatMenu = false
                            },
                        )
                        formats.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format.name) },
                                onClick = {
                                    formatId = format.id
                                    formatMenu = false
                                },
                            )
                        }
                    }
                }
                if (formats.isEmpty()) {
                    Text(
                        "还没有别的格式。可以在设置的「输出格式」里新建。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Only worth offering while the note may not exist yet.
                if (existing?.seeded != true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同时创建笔记骨架", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "笔记还不存在时勾选。已存在的笔记勾了会重复写入 frontmatter。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = createSkeleton, onCheckedChange = { createSkeleton = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        TargetDraft(
                            rowId = existing?.id,
                            name = name,
                            author = author,
                            year = year,
                            path = path,
                            heading = heading,
                            formatId = formatId,
                            createSkeleton = createSkeleton,
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
