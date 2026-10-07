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

/**
 * Adds a book (or any other destination) to the saved targets.
 *
 * Typing the book once here is what replaces guessing it out of the share text: a wrong
 * guess files a highlight into a brand new note, and the note it should have gone to is
 * usually sitting right there already.
 */
@Composable
fun AddTargetDialog(
    defaultHeading: String,
    derivePath: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        author: String,
        year: String,
        path: String,
        heading: String,
        createSkeleton: Boolean,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var heading by remember { mutableStateOf(defaultHeading) }
    // The path follows the name until the user overrules it.
    var pathEdited by remember { mutableStateOf(false) }
    var pathOverride by remember { mutableStateOf("") }
    var createSkeleton by remember { mutableStateOf(false) }

    val path = if (pathEdited) pathOverride else derivePath(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加目标") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("书名") },
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
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, author, year, path, heading, createSkeleton) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
