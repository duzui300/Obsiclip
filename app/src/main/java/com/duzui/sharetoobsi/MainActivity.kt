package com.duzui.sharetoobsi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duzui.sharetoobsi.ui.HistoryScreen
import com.duzui.sharetoobsi.ui.ImportReviewScreen
import com.duzui.sharetoobsi.ui.SettingsScreen
import com.duzui.sharetoobsi.ui.ShareScreen
import com.duzui.sharetoobsi.send.accessibilitySettingsIntent
import com.duzui.sharetoobsi.send.CollectorStatus
import com.duzui.sharetoobsi.send.collectorStatus
import com.duzui.sharetoobsi.ui.theme.ShareTransTheme

class MainActivity : ComponentActivity() {

    private enum class Screen { Share, Settings, History, Import }

    private var incoming by mutableStateOf<IncomingShare?>(null)
    private var screen by mutableStateOf(Screen.Share)

    /** Bumped when a fresh launch or a tile tap should pull in the clipboard. */
    private var launcherAdoptRequests by mutableStateOf(0)
    private var tileAdoptRequests by mutableStateOf(0)

    /** A target a Direct Share shortcut picked, applied once the target list has loaded. */
    private var requestedTargetId by mutableStateOf<Long?>(null)

    /** Bumped when the collector's notification opens this screen with a result waiting. */
    private var importRequests by mutableStateOf(0)

    /** Whether the collector is really listening — not whether a setting claims it is. */
    private var collector by mutableStateOf(CollectorStatus.NotEnabled)

    /** Needed on API 33+ for the "collection finished" notification to appear at all. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        if (isFromImport(intent)) importRequests = 1
        collector = collectorStatus(this)

        setContent {
            ShareTransTheme {
                val viewModel: ShareViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val message by viewModel.message.collectAsStateWithLifecycle()

                LaunchedEffect(incoming) { viewModel.onShare(incoming) }
                LaunchedEffect(importRequests) {
                    if (importRequests > 0) viewModel.onImportOpened()
                }
                // A finished collection takes over the screen; there is nothing to do with
                // it on the capture screen, which is about one highlight.
                LaunchedEffect(screen) {
                    if (screen == Screen.Settings) viewModel.refreshPendingImport()
                }
                LaunchedEffect(state.importEntries.isNotEmpty()) {
                    if (state.importEntries.isNotEmpty()) screen = Screen.Import
                }
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
                        onAutoReadClipboard = viewModel::setAutoReadClipboard,
                        onAutoWriteImports = viewModel::setAutoWriteImports,
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
                        collectorStatus = collector,
                        onArmKindleImport = {
                            askForNotificationPermission()
                            viewModel.armKindleImport()
                        },
                        onOpenAccessibilitySettings = {
                            startActivity(accessibilitySettingsIntent())
                        },
                        onOpenPendingImport = viewModel::onImportOpened,
                        onOpenHistory = { screen = Screen.History },
                        onRetryOutbox = viewModel::retryOutbox,
                    )

                    Screen.Import -> ImportReviewScreen(
                        entries = state.importEntries,
                        targetPath = state.targetPath,
                        onToggle = viewModel::setImportIncluded,
                        onEdit = viewModel::setImportText,
                        onSelectAll = viewModel::setAllImportsIncluded,
                        onWrite = viewModel::writeImport,
                        onDismiss = {
                            viewModel.dismissImport()
                            screen = Screen.Share
                        },
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

    override fun onResume() {
        super.onResume()
        // The user may have just come back from enabling it in system settings.
        collector = collectorStatus(this)
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
        // Before the launcher check: this intent is also an ACTION_MAIN with no text, so
        // the launcher branch would swallow it and the collected batch would never open.
        if (isFromImport(intent)) {
            importRequests++
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

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun isFromImport(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_FROM_IMPORT, false) == true

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
        // Only close if something actually opened. Finishing with nothing to go back to
        // drops the user on the launcher, which is what a write with no source app — from
        // the clipboard, the tile, or a collected batch — used to do.
        val launch = packageManager.getLaunchIntentForPackage(sourcePackage) ?: return
        startActivity(launch)
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

        /** Set by the collector's notification, meaning a finished run is waiting. */
        const val EXTRA_FROM_IMPORT = "fromImport"

        fun intentForImport(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(EXTRA_FROM_IMPORT, true)
    }
}
