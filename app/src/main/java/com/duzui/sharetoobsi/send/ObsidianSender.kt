package com.duzui.sharetoobsi.send

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.duzui.sharetoobsi.R
import com.duzui.sharetoobsi.domain.ObsidianUri
import com.duzui.sharetoobsi.domain.WriteRequest

sealed interface SendOutcome {
    /**
     * The intent left the app. That is the whole of what can be known here: a missing
     * heading makes the plugin silently do nothing, and `x-success` fires in that case too.
     */
    data class Dispatched(val uri: String, val viaClipboard: Boolean) : SendOutcome

    data object NoObsidian : SendOutcome

    data class Failed(val message: String) : SendOutcome
}

class ObsidianSender(private val context: Context) {

    companion object {
        private const val OBSIDIAN_URI = "obsidian://"

        /**
         * Above this, the payload rides on the clipboard instead of inside the URI.
         * 4000 characters is the largest inline payload actually measured working on this
         * device, so the limit sits well clear of it rather than at the measured edge.
         */
        const val INLINE_LIMIT = 16_000
    }

    fun isObsidianInstalled(): Boolean =
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(OBSIDIAN_URI)), 0)
            .isNotEmpty()

    fun send(request: WriteRequest): SendOutcome {
        val viaClipboard = request.useClipboard || request.content.length > INLINE_LIMIT
        val uri = ObsidianUri.build(request.copy(useClipboard = viaClipboard))

        // Obsidian reads this once it is in the foreground, which it will be after
        // startActivity. A background read would be blocked from Android 10 on.
        if (viaClipboard) putOnClipboard(request.content)

        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            SendOutcome.Dispatched(uri, viaClipboard)
        } catch (_: ActivityNotFoundException) {
            SendOutcome.NoObsidian
        } catch (e: RuntimeException) {
            SendOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun putOnClipboard(text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_label), text))
    }
}
