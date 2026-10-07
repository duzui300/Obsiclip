package com.duzui.sharetoobsi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duzui.sharetoobsi.ui.ShareScreen
import com.duzui.sharetoobsi.ui.theme.ShareTransTheme

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming = readIncoming()
        setContent {
            ShareTransTheme {
                val viewModel: ShareViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val message by viewModel.message.collectAsStateWithLifecycle()

                LaunchedEffect(incoming) { viewModel.onShare(incoming) }

                ShareScreen(
                    state = state,
                    message = message,
                    onProfileChange = viewModel::setProfile,
                    onBookTitleChange = viewModel::setBookTitle,
                    onBookAuthorChange = viewModel::setBookAuthor,
                    onBookYearChange = viewModel::setBookYear,
                    onTagsChange = viewModel::setTags,
                    onModeChange = viewModel::setMode,
                    onPayloadEdit = viewModel::editPayload,
                    onRegenerate = viewModel::regenerate,
                    onSend = viewModel::send,
                    onMessageShown = viewModel::messageShown,
                )
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
