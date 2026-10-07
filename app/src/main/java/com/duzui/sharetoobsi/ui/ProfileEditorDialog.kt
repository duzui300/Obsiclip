package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.data.ProfileEntity
import com.duzui.sharetoobsi.domain.compileRules

/**
 * Writes a rule set. Both rule fields take one regex per line.
 *
 * Rules that do not compile are named rather than dropped: a regex that silently matches
 * nothing looks exactly like a regex that works, and that is the worst thing a cleaning
 * rule can do.
 */
@Composable
fun ProfileEditorDialog(
    existing: ProfileEntity?,
    placeholderTemplate: String,
    onDismiss: () -> Unit,
    onSave: (rowId: Long?, name: String, lineRules: String, inlineRules: String, template: String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var lineRules by remember { mutableStateOf(existing?.lineRules.orEmpty()) }
    var inlineRules by remember { mutableStateOf(existing?.inlineRules.orEmpty()) }
    var template by remember { mutableStateOf(existing?.template.orEmpty()) }

    val invalidLines = compileRules(lineRules).invalid
    val invalidInlines = compileRules(inlineRules).invalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建规则" else "编辑规则") },
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

                RulesField(
                    label = "删除整行的规则",
                    hint = "匹配到的整行会被删掉，每行一条正则",
                    value = lineRules,
                    onValueChange = { lineRules = it },
                    invalid = invalidLines,
                )

                RulesField(
                    label = "行内删除的规则",
                    hint = "匹配到的部分会被删掉，保留该行其余内容",
                    value = inlineRules,
                    onValueChange = { inlineRules = it },
                    invalid = invalidInlines,
                )

                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("输出模板（可留空）") },
                    supportingText = { Text("留空则用全局模板。占位符同全局。") },
                    placeholder = { Text(placeholderTemplate) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(existing?.id, name, lineRules, inlineRules, template) },
                enabled = name.isNotBlank() && invalidLines.isEmpty() && invalidInlines.isEmpty(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RulesField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    invalid: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = { Text(hint) },
            isError = invalid.isNotEmpty(),
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
        )
        if (invalid.isNotEmpty()) {
            Text(
                "这些正则不合法，保存前先修：" + invalid.joinToString("　"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
