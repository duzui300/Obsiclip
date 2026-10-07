package com.duzui.sharetoobsi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duzui.sharetoobsi.ui.theme.ShareTransTheme

/** What arrived from the share sheet or the text-selection toolbar. */
data class IncomingShare(
    val action: String,
    val text: String,
    val sourcePackage: String?,
)

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming = readIncoming()
        setContent {
            ShareTransTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Placeholder(
                        share = incoming,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = readIncoming()
    }

    private fun readIncoming(): IncomingShare? {
        val intent = intent ?: return null
        val action = intent.action ?: return null

        // A share carries the text in a different extra depending on the action.
        val text: String? = when (action) {
            Intent.ACTION_SEND ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()

            Intent.ACTION_SEND_MULTIPLE ->
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                    ?.joinToString("\n\n") { it.toString() }

            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            else -> null
        }

        if (text.isNullOrBlank()) return null
        return IncomingShare(action, text, sourcePackage())
    }

    /** The system fills the referrer with the sharing app when we are started from the share sheet. */
    private fun sourcePackage(): String? =
        referrer?.host ?: referrer?.authority
}

@Composable
private fun Placeholder(share: IncomingShare?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("摘录入 Obsidian", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (share == null) {
            Text(
                "从任意 App 分享文字，或用「处理文本」选中一段文字，即可从这里写入 Obsidian。",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text("来源：${share.sourcePackage ?: "未知"}", style = MaterialTheme.typography.bodySmall)
            Text("动作：${share.action}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text(share.text.take(1000), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
