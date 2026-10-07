package com.duzui.sharetoobsi.send

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.duzui.sharetoobsi.MainActivity
import com.duzui.sharetoobsi.data.TargetEntity

/**
 * Puts the saved targets in the share sheet's Direct Share row, so a highlight can go
 * straight to the book without opening this app at all.
 *
 * The system fills that row from dynamic shortcuts that look like share targets — an
 * `ACTION_SEND` intent aimed back at us. The shortcut carries no text; the sharing app
 * still supplies it, and our activity reads both.
 */
object ShareShortcuts {

    /** Direct Share shows a handful; more than this is noise the system drops anyway. */
    private const val MAX_TARGETS = 4

    fun sync(context: Context, targets: List<TargetEntity>) {
        if (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) <= 0) return

        val shortcuts = targets.take(MAX_TARGETS).map { shortcut(context, it) }
        // Throws when rate-limited or over the per-activity cap. Neither is worth
        // interrupting a write over: the chips on the share screen do the same job.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, target: TargetEntity): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, "target-${target.id}")
            .setShortLabel(target.name)
            .setLongLived(true)
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(MainActivity.EXTRA_TARGET_ID, target.id)
            )
            .build()
}
