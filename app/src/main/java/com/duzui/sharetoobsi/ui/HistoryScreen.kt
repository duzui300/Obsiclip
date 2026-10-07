package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.data.HistoryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/**
 * What has been written, newest first.
 *
 * Resend is offered because "dispatched" only ever meant the intent left the app — if a
 * write quietly did not land, re-sending is the only remedy available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<HistoryEntity>,
    onBack: () -> Unit,
    onResend: (HistoryEntity) -> Unit,
    onClear: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.history_clear)) }
                    }
                },
            )
        },
    ) { insets ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(24.dp),
            ) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(insets)) {
            items(entries, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stamp(entry.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            outcomeLabel(entry),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (entry.outcome == "Dispatched") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    Text(
                        buildString {
                            append(entry.targetPath)
                            entry.heading?.let { append(" › ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        entry.payload,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedButton(onClick = { onResend(entry) }) { Text(stringResource(R.string.history_resend)) }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun stamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(STAMP)

private fun outcomeLabel(entry: HistoryEntity): String = when (entry.outcome) {
    "Dispatched" -> if (entry.viaClipboard) "已发送（剪贴板）" else "已发送"
    "NoObsidian" -> "没找到 Obsidian"
    else -> "失败"
}
