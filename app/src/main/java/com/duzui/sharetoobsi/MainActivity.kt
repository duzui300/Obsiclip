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
import com.duzui.sharetoobsi.ui.SettingsScreen
import com.duzui.sharetoobsi.ui.ShareScreen
import com.duzui.sharetoobsi.ui.theme.ShareTransTheme

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<IncomingShare?>(null)
    private var showSettings by mutableStateOf(false)

    private val container by lazy { AppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (isReturnCallback(intent)) {
            returnToSourceApp()
            return
        }

        incoming = readIncoming()
        setContent {
            ShareTransTheme {
                val viewModel: ShareViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val message by viewModel.message.collectAsStateWithLifecycle()

                LaunchedEffect(incoming) { viewModel.onShare(incoming) }

                if (showSettings) {
                    SettingsScreen(
                        settings = state.settings,
                        targets = state.savedTargets,
                        onBack = { showSettings = false },
                        onVault = viewModel::setVault,
                        onHeading = viewModel::setHeading,
                        onTemplate = viewModel::setTemplate,
                        onPathTemplate = viewModel::setPathTemplate,
                        onInboxPath = viewModel::setInboxPath,
                        onTags = viewModel::setTags,
                        onMode = viewModel::setMode,
                        onSilent = viewModel::setSilent,
                        onReturnToSource = viewModel::setReturnToSource,
                        onCleanup = viewModel::setCleanup,
                        onSaveTarget = viewModel::addTarget,
                        onDerivePath = viewModel::previewPath,
                        onDeleteTarget = viewModel::deleteTarget,
                        onRetryOutbox = viewModel::retryOutbox,
                    )
                } else {
                    ShareScreen(
                        state = state,
                        message = message,
                        onProfileChange = viewModel::setProfile,
                        onModeChange = viewModel::setMode,
                        onSelectTarget = viewModel::selectTarget,
                        onAddTarget = viewModel::addTarget,
                        onCreateSkeleton = viewModel::createSkeletonFor,
                        onDerivePath = viewModel::previewPath,
                        onReadClipboard = {
                            viewModel.readFromClipboard(ClipboardReader.read(this@MainActivity))
                        },
                        onPayloadEdit = viewModel::editPayload,
                        onRegenerate = viewModel::regenerate,
                        onSend = viewModel::send,
                        onOpenSettings = { showSettings = true },
                        onMessageShown = viewModel::messageShown,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isReturnCallback(intent)) {
            returnToSourceApp()
            return
        }
        incoming = readIncoming()
    }

    /** Obsidian hands control back on this URI once the write is done. */
    private fun isReturnCallback(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == RETURN_SCHEME

    private fun returnToSourceApp() {
        val sourcePackage = container.pendingReturn.take()
        if (sourcePackage != null) {
            // Null when the app is not launchable from a drawer, or is no longer visible
            // to us; in that case simply closing leaves the user in Obsidian, which is
            // where they already are.
            packageManager.getLaunchIntentForPackage(sourcePackage)?.let { launch ->
                startActivity(launch)
            }
        }
        finish()
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

    private companion object {
        const val RETURN_SCHEME = "sharetoobsi"
    }
}
