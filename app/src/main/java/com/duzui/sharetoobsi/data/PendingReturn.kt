package com.duzui.sharetoobsi.data

import android.content.Context

/**
 * Remembers which app to hand the user back to while Obsidian processes a write.
 *
 * SharedPreferences rather than DataStore on purpose: the hand-back happens inside an
 * intent handler, where a suspend read would be in the way.
 */
class PendingReturn(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("pending", Context.MODE_PRIVATE)

    fun remember(packageName: String) {
        prefs.edit().putString(KEY, packageName).apply()
    }

    /** Reads and clears in one step, so a stale value can never fire a second time. */
    fun take(): String? {
        val value = prefs.getString(KEY, null)
        if (value != null) prefs.edit().remove(KEY).apply()
        return value?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val KEY = "returnPackage"
    }
}
