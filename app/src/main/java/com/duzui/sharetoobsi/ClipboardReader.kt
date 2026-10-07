package com.duzui.sharetoobsi

import android.content.ClipboardManager
import android.content.Context

/**
 * Reads the clipboard, which Android 10+ allows only while we are the focused app — true
 * whenever this is called, since it comes from a tap.
 *
 * This is the way around a reading app whose share sheet refuses a long selection: select
 * and Copy, then read it here. Kindle's Copy is also *cleaner* than its share — no
 * preamble and no store link, so there is less to strip.
 */
object ClipboardReader {

    fun read(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        return clip.getItemAt(0)
            .coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }
}
