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
import com.duzui.sharetoobsi.ui.HistoryScreen
import com.duzui.sharetoobsi.ui.SettingsScreen
import com.duzui.sharetoobsi.ui.ShareScreen
import com.duzui.sharetoobsi.ui.theme.ShareTransTheme

class MainActivity : ComponentActivity() {

    private enum class Screen { Share, Settings, History }

    private var incoming by mutableStateOf<IncomingShare?>(null)
    private var screen by mutableStateOf(Screen.Share)

    /** Bumped when a fresh launch or a tile tap should pull in the clipboard. */
    private var launcherAdoptRequests by mutableStateOf(0)
    private var tileAdoptRequests by mutableStateOf(0)

    /** A target a Direct Share shortcut picked, applied once the target list has loaded. */
    private var requestedTargetId by mutableStateOf<Long?>(null)

    private val container by lazy { AppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (isReturnCallback(intent)) {
            returnToSourceApp()
            return
        }

        incoming = readIncoming()
        // Only a genuine fresh launch counts. A configuration change re-runs onCreate, and
        // re-adopting there would throw away an edit in progress.
        if (savedInstanceState == null && incoming == null) launcherAdoptRequests = 1
        if (isFromTile(intent)) tileAdoptRequests = 1
        requestedTargetId = targetFrom(intent)

        setContent {
            ShareTransTheme {
                val viewModel: ShareViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val message by viewModel.message.collectAsStateWithLifecycle()

                LaunchedEffect(incoming) { viewModel.onShare(incoming) }
                LaunchedEffect(requestedTargetId) {
                    requestedTargetId?.let { id ->
                        viewModel.selectTarget(id)
                        requestedTargetId = null
                    }
                }
                // Opening the app directly may adopt the clipboard, if the user asked for it.
                LaunchedEffect(launcherAdoptRequests) {
                    if (launcherAdoptRequests > 0) viewModel.adoptClipboardIfEnabled()
                }
                // The tile exists to capture the clipboard, so it always does.
                LaunchedEffect(tileAdoptRequests) {
                    if (tileAdoptRequests > 0) viewModel.adoptClipboardIfEnabled(force = true)
                }

                when (screen) {
                    Screen.Share -> ShareScreen(
                        state = state,
                        message = message,
                        onProfileChange = viewModel::setProfile,
                        onSelectTarget = viewModel::selectTarget,
                        onMoveTarget = viewModel::moveTarget,
                        onSaveTarget = viewModel::saveTarget,
                        onSelectBook = viewModel::selectBook,
                        onMoveBook = viewModel::moveBook,
                        onSaveBook = viewModel::saveBook,
                        onDeriveSkeletonPath = viewModel::previewBookPath,
                        onReadClipboard = {
                            viewModel.readFromClipboard(ClipboardReader.read(this@MainActivity))
                        },
                        onPayloadEdit = viewModel::editPayload,
                        onRegenerate = viewModel::regenerate,
                        onSend = viewModel::send,
                        onOpenSettings = { screen = Screen.Settings },
                        onMessageShown = viewModel::messageShown,
                    )

                    Screen.Settings -> SettingsScreen(
                        state = state,
                        onBack = { screen = Screen.Share },
                        onVault = viewModel::setVault,
                        onHeading = viewModel::setHeading,
                        onTemplate = viewModel::setTemplate,
                        onPathTemplate = viewModel::setPathTemplate,
                        onTags = viewModel::setTags,
                        onMode = viewModel::setMode,
                        onSilent = viewModel::setSilent,
                        onAutoReadClipboard = viewModel::setAutoReadClipboard,
                        onReturnToSource = viewModel::setReturnToSource,
                        onCleanup = viewModel::setCleanup,
                        onDefaultProfileId = viewModel::setDefaultProfileId,
                        onSaveProfile = viewModel::saveUserProfile,
                        onDeleteProfile = viewModel::deleteUserProfile,
                        onSetAppMapping = viewModel::setAppMapping,
                        onClearAppMapping = viewModel::clearAppMapping,
                        onSaveTarget = viewModel::saveTarget,
                        onMoveTarget = viewModel::moveTarget,
                        onDeleteTarget = viewModel::deleteTarget,
                        onDefaultTargetId = viewModel::setDefaultTargetId,
                        onSaveBook = viewModel::saveBook,
                        onMoveBook = viewModel::moveBook,
                        onDeleteBook = viewModel::deleteBook,
                        onSaveFormat = viewModel::saveFormat,
                        onDeleteFormat = viewModel::deleteFormat,
                        onDeriveSkeletonPath = viewModel::previewBookPath,
                        onOpenHistory = { screen = Screen.History },
                        onRetryOutbox = viewModel::retryOutbox,
                    )

                    Screen.History -> HistoryScreen(
                        entries = state.history,
                        onBack = { screen = Screen.Settings },
                        onResend = viewModel::resend,
                        onClear = viewModel::clearHistory,
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
        if (isFromTile(intent)) {
            tileAdoptRequests++
            return
        }
        // Brought to the front from the launcher while still alive. This is the common
        // second capture of a session, and it has to behave like a fresh launch — the
        // text on screen is from last time and is stale by definition.
        if (isLauncherEntry(intent)) {
            launcherAdoptRequests++
            return
        }
        requestedTargetId = targetFrom(intent)
        incoming = readIncoming()
        // A share arrived while settings were open; it belongs on the share screen.
        if (incoming != null) screen = Screen.Share
    }

    private fun isLauncherEntry(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.getStringExtra(Intent.EXTRA_TEXT) == null

    private fun isFromTile(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_FROM_TILE, false) == true

    private fun targetFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_TARGET_ID, -1L)?.takeIf { it >= 0 }

    /** Obsidian hands control back on this URI once the write is done. */
    private fun isReturnCallback(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == RETURN_SCHEME

    /**
     * Obsidian hands control back on this URI once the write is done.
     *
     * With nothing remembered this is the quiet-write case: the callback has already
     * brought us back to the front, so staying put is the whole point, and the activity
     * must not finish or the user would land wherever they were before.
     */
    private fun returnToSourceApp() {
        val sourcePackage = container.pendingReturn.take() ?: return
        // Null when the app is not launchable from a drawer, or is no longer visible to
        // us; in that case simply closing leaves the user in Obsidian, which is where
        // they already are.
        packageManager.getLaunchIntentForPackage(sourcePackage)?.let { launch ->
            startActivity(launch)
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

    companion object {
        private const val RETURN_SCHEME = "sharetoobsi"

        /** Set by [com.duzui.sharetoobsi.ui.QuoteTileService] to ask for a clipboard capture. */
        const val EXTRA_FROM_TILE = "fromTile"

        /** Set by a Direct Share shortcut to say which saved target was chosen. */
        const val EXTRA_TARGET_ID = "targetId"
    }
}
