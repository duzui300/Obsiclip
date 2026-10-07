package com.duzui.sharetoobsi.send

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.duzui.sharetoobsi.MainActivity
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.data.CollectedItem
import com.duzui.sharetoobsi.data.KindleImportStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collects highlights off Kindle's notebook screen.
 *
 * Kindle refuses to let a long passage be selected in the reading view, which is why both
 * sharing and copying one fail. The notebook screen is a different story: each highlight
 * sits in the accessibility tree as plain text, so reading the tree sidesteps the
 * restriction completely — no selection, no clipboard, and nothing left to clean up.
 *
 * Everything here is identified by structure, never by wording. Kindle's UI strings are
 * localised, so matching on `新增備註` or `Page 221` would tie this to one language.
 */
/** Sent by the app the moment it arms, because no event can be relied on to follow. */
const val ACTION_ARM_KINDLE_IMPORT = "com.duzui.sharetoobsi.ARM_KINDLE_IMPORT"

class KindleNotebookService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: KindleImportStore

    /** Quote texts keyed by their opening characters, so a collapsed copy cannot replace an
     *  expanded one and vice versa. */
    private val collected = LinkedHashMap<String, CollectedItem>()

    private var running = false
    private var notebookSeen = false
    private var lastAttemptAt = 0L

    /**
     * How long after arming the service will keep looking for the notebook.
     *
     * Waiting for a window change is not enough: if the user is *already* on the notebook
     * page, switching back to Kindle changes nothing about which window is up, so no event
     * arrives and the run never starts. Bounded, backgrounded and throttled, so this cannot
     * become the stall that reading the tree on every event used to cause.
     */
    private var watchingUntil = 0L

    private val armReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            watchingUntil = SystemClock.elapsedRealtime() + WATCH_WINDOW_MS
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = KindleImportStore(this)
        store.connected = true
        ContextCompat.registerReceiver(
            this,
            armReceiver,
            IntentFilter(ACTION_ARM_KINDLE_IMPORT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        store.connected = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(armReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != KINDLE_PACKAGE) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            notebookSeen = event.className?.toString()
                ?.contains(NOTEBOOK_CLASS, ignoreCase = true) == true
        }

        // Deliberately cheap: this callback runs on the service's main thread and fires
        // constantly. Reading the window tree is a blocking round trip that waits on the
        // observed app — and when the window on top is not one this service may read, it
        // waits out the full timeout. Doing that here stalls the whole accessibility
        // pipeline, which is what made the device feel stuck.
        val watching = SystemClock.elapsedRealtime() < watchingUntil
        if (!store.armed || running) return
        if (!notebookSeen && !watching) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttemptAt < ATTEMPT_INTERVAL_MS) return
        lastAttemptAt = now

        running = true
        scope.launch {
            try {
                val root = rootInActiveWindow
                if (root == null || !looksLikeNotebook(root)) return@launch

                // Committed: from here the run owns the arming flag.
                store.armed = false
                val droveItself = runCollection()
                val items = collected.values.toList()
                collected.clear()
                store.publish(items, autoScrolled = droveItself)
                notifyFinished(items.size, droveItself)
            } finally {
                running = false
            }
        }
    }

    // ---- collection --------------------------------------------------------

    /** @return whether the list was scrolled to the end from here, rather than by hand. */
    private suspend fun runCollection(): Boolean {
        var lastSignature = ""
        var stablePasses = 0
        var passes = 0
        var scrolled = false

        while (passes < MAX_PASSES) {
            passes++
            val root = rootInActiveWindow ?: break
            harvest(root)

            val signature = signatureOf(root)
            if (signature == lastSignature) {
                stablePasses++
                // Two passes with identical content means the list has stopped moving.
                if (stablePasses >= 2) return true
            } else {
                stablePasses = 0
                lastSignature = signature
            }

            val scrollable = findScrollable(root) ?: return scrolled
            if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                scrolled = true
                delay(SCROLL_SETTLE_MS)
            } else {
                // A scroll that refuses is what the end of a list looks like, not a failure
                // to drive it. Reporting it as failure told the user to scroll by hand
                // after the list had already been read to the bottom.
                return scrolled
            }
        }
        return true
    }

    private fun harvest(root: AccessibilityNodeInfo) {
        forEachLeaf(root) { node ->
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.length < MIN_QUOTE) return@forEachLeaf

            val key = text.take(PREFIX)
            val existing = collected[key]
            // The list shows a collapsed and later an expanded copy; the longest wins.
            if (existing == null || text.length > existing.text.length) {
                collected[key] = CollectedItem(
                    text = text,
                    page = pageNear(node, text),
                    suspect = text.endsWith("…") || text.endsWith("..."),
                )
            }
        }
    }

    /**
     * Best effort, and only kept when it looks like a page rather than the entry's number:
     * of the short labels beside a quote, the page is the longest one carrying a digit.
     * Kindle writes these differently in every language, so nothing is assumed about them.
     */
    private fun pageNear(node: AccessibilityNodeInfo, quote: String): String? {
        var parent = node.parent
        repeat(SEARCH_DEPTH) {
            parent ?: return null
            val candidates = mutableListOf<String>()
            forEachLeaf(parent!!) { leaf ->
                val text = leaf.text?.toString()?.trim().orEmpty()
                if (text.length in 1..MAX_PAGE_LABEL &&
                    text != quote &&
                    text.any { it.isDigit() }
                ) {
                    candidates += text
                }
            }
            candidates.maxByOrNull { it.length }?.let { return it }
            parent = parent!!.parent
        }
        return null
    }

    /** A cheap fingerprint of what is on screen, used to notice the list has stopped moving. */
    private fun signatureOf(root: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        forEachLeaf(root) { node ->
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it.take(24) }
        }
        return parts.joinToString("|")
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            if (found != null) return
            if (node.isScrollable) {
                found = node
                return
            }
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(root)
        return found
    }

    /** The notebook is a scrollable list of quotes; the reading view is not. */
    private fun looksLikeNotebook(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        if (findScrollable(root) == null) return false
        var quotes = 0
        forEachLeaf(root) { node ->
            if (node.text?.toString()?.trim().orEmpty().length >= MIN_QUOTE) quotes++
        }
        return quotes >= 2
    }

    private fun forEachLeaf(root: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        if (root.childCount == 0) {
            action(root)
            return
        }
        for (index in 0 until root.childCount) {
            root.getChild(index)?.let { forEachLeaf(it, action) }
        }
    }

    // ---- telling the user --------------------------------------------------

    private fun notifyFinished(count: Int, autoScrolled: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.kindle_import_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        val hasContent = count > 0
        val open = PendingIntent.getActivity(
            this,
            0,
            MainActivity.intentForImport(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(
                if (hasContent) getString(R.string.kindle_import_title, count)
                else getString(R.string.kindle_import_channel)
            )
            .setContentText(
                when {
                    !hasContent -> getString(R.string.kindle_import_nothing)
                    autoScrolled -> getString(R.string.kindle_import_body)
                    else -> getString(R.string.kindle_import_body_manual)
                }
            )
            .setAutoCancel(true)
            .setContentIntent(open)

        // A run that could not scroll itself leaves the user in Kindle; give them a way to
        // say "that is all of it" without hunting for the app.
        if (hasContent && !autoScrolled) {
            builder.addAction(
                0,
                getString(R.string.kindle_import_finish),
                PendingIntent.getActivity(
                    this,
                    1,
                    MainActivity.intentForImport(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val KINDLE_PACKAGE = "com.amazon.kindle"
        const val NOTEBOOK_CLASS = "notebook"

        /** Shorter than this is UI chrome: the shortest real label here is seven characters. */
        const val MIN_QUOTE = 8
        const val MAX_PAGE_LABEL = 16
        const val PREFIX = 24
        const val SEARCH_DEPTH = 4
        const val MAX_PASSES = 60
        const val SCROLL_SETTLE_MS = 350L
        /** Floor between tree reads, so a burst of events cannot pile them up. */
        const val ATTEMPT_INTERVAL_MS = 800L
        const val WATCH_WINDOW_MS = 40_000L

        const val CHANNEL_ID = "kindle_import"
        const val NOTIFICATION_ID = 4711
    }
}
