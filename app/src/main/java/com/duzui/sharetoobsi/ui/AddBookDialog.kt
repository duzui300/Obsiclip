package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.duzui.sharetoobsi.BookDraft
import com.duzui.sharetoobsi.data.BookEntity

/**
 * Adds or edits something being read: title, author, year, and nothing about where its
 * quotes go.
 *
 * The skeleton switch belongs here because a skeleton is a book's note. It is offered only
 * when creating, and writes to wherever the currently chosen target would put this book —
 * shown below the switch so there is no guessing about which note it will touch.
 */
@Composable
fun AddBookDialog(
    existing: BookEntity?,
    deriveSkeletonPath: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (BookDraft) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var author by remember { mutableStateOf(existing?.author.orEmpty()) }
    var year by remember { mutableStateOf(existing?.year.orEmpty()) }
    var createSkeleton by remember { mutableStateOf(false) }

    val skeletonPath = if (createSkeleton) deriveSkeletonPath(title) else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加书籍" else "编辑书籍") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    supportingText = { Text("模板里的 {title}") },
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

                if (existing == null) {
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
                    if (createSkeleton) {
                        Text(
                            "会写到：" + skeletonPath.ifBlank { "（先填书名）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        BookDraft(
                            rowId = existing?.id,
                            title = title,
                            author = author,
                            year = year,
                            createSkeleton = createSkeleton,
                        )
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
