package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.data.FormatEntity
import com.duzui.sharetoobsi.domain.Template

/**
 * Named output shapes, so a target can pick one instead of everybody editing the one
 * global template until it stops suiting anyone.
 *
 * The global template stays as "the default", which is what a target with no format of its
 * own gets — that keeps the existing edited template meaningful rather than migrating it.
 */
@Composable
fun FormatsSection(
    formats: List<FormatEntity>,
    onSave: (rowId: Long?, name: String, template: String) -> Unit,
    onDelete: (FormatEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<FormatEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        FormatEditorDialog(
            existing = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { id, name, template ->
                onSave(id, name, template)
                creating = false
                editing = null
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "给不同的笔记配不同的样式。目标没选格式时用上面的默认格式模板。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Text("新建格式")
        }

        formats.forEach { format ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(format.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        format.template.replace("\n", "⏎"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { editing = format }) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = { onDelete(format) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun FormatEditorDialog(
    existing: FormatEntity?,
    onDismiss: () -> Unit,
    onSave: (rowId: Long?, name: String, template: String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var template by remember { mutableStateOf(existing?.template.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建格式" else "编辑格式") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("模板") },
                    supportingText = {
                        Text("占位符：" + Template.placeholders.joinToString(" ") { "{$it}" })
                    },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "例：`{text}` 换行后接 `> — {author}《{title}》{year} {tags}`",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(existing?.id, name, template) },
                enabled = name.isNotBlank() && template.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
