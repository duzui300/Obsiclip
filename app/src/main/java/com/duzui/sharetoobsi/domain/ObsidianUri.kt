package com.duzui.sharetoobsi.domain

import java.net.URLEncoder

enum class WriteMode {
    /** `obsidian://adv-uri`, from the Advanced URI plugin: can target a heading. */
    ADVANCED,

    /** `obsidian://new`: built in, but can only append to the end of a file. */
    OFFICIAL,
}

data class WriteRequest(
    val vault: String,
    /** Vault-relative, e.g. `30-Reading/Book/反建築論.md`. */
    val filePath: String,
    /** Section to append under. Ignored by [WriteMode.OFFICIAL], which has no such parameter. */
    val heading: String? = null,
    val content: String = "",
    val mode: WriteMode = WriteMode.ADVANCED,
    /**
     * Leaves the note unopened. Not a user setting: Obsidian is always pulled forward to
     * handle the URI, so the only thing this decides is whether it lands on the note.
     * Writes open it; creating a skeleton does not, because that is setup, not reading.
     */
    val silent: Boolean = false,
    /** Put the payload on the clipboard and send `clipboard=true` instead of inlining it. */
    val useClipboard: Boolean = false,
    /** Advanced URI inserts a single `\n` by default; the official action inserts `\n\n` itself. */
    val separator: String = "\n\n",
    val successCallback: String? = null,
)

/**
 * Builds the `obsidian://` URIs, matching what was measured on the device:
 *
 * - `append` is mandatory on both paths. Without it the official action silently does
 *   nothing to an existing file, and on the Advanced path `mode=append` is what makes
 *   the write happen at all.
 * - With a `heading` the Advanced URI silently no-ops if that heading is absent from the
 *   file, and reports nothing. Callers must guarantee the heading exists.
 * - `separator` is passed explicitly so both paths separate entries with a blank line.
 */
object ObsidianUri {

    private const val ENCODING = "UTF-8"

    /**
     * Percent-encodes for a query value. [URLEncoder] turns spaces into `+`, which is a
     * literal plus in a URI query, so those get rewritten; `/` must become `%2F` (which
     * [URLEncoder] already does), otherwise the path is misinterpreted.
     */
    fun encode(value: String): String =
        URLEncoder.encode(value, ENCODING).replace("+", "%20")

    fun build(request: WriteRequest): String = when (request.mode) {
        WriteMode.ADVANCED -> buildAdvanced(request)
        WriteMode.OFFICIAL -> buildOfficial(request)
    }

    private fun buildAdvanced(request: WriteRequest): String {
        val parts = mutableListOf(
            "vault=${encode(request.vault)}",
            "filepath=${encode(request.filePath)}",
            "mode=append",
        )
        request.heading?.takeIf { it.isNotBlank() }?.let { parts += "heading=${encode(it)}" }
        parts += payload(request, key = "data")
        parts += "separator=${encode(request.separator)}"
        if (request.silent) parts += "openmode=silent"
        request.successCallback?.let { parts += "x-success=${encode(it)}" }
        return "obsidian://adv-uri?" + parts.joinToString("&")
    }

    private fun buildOfficial(request: WriteRequest): String {
        val parts = mutableListOf(
            "vault=${encode(request.vault)}",
            "file=${encode(request.filePath)}",
            "append=true",
        )
        parts += payload(request, key = "content")
        if (request.silent) parts += "silent=true"
        request.successCallback?.let { parts += "x-success=${encode(it)}" }
        return "obsidian://new?" + parts.joinToString("&")
    }

    /** The plugin calls the payload parameter `data`, the built-in action calls it `content`. */
    private fun payload(request: WriteRequest, key: String): String =
        if (request.useClipboard) "clipboard=true" else "$key=${encode(request.content)}"
}
